package pl.touk.nussknacker.engine.requestresponse

import cats.Monad
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel, WriterT}
import cats.implicits.toFunctorOps
import io.circe.Json
import io.circe.syntax._
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessName}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.ScenarioInterpreterFactory.ScenarioInterpreterWithLifecycle
import pl.touk.nussknacker.engine.lite.TestRunner.EffectUnwrapper
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.CapabilityTransformer
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{EndResult, ScenarioInputBatch}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.{LiteEngineRuntimeContext, LiteEngineRuntimeContextPreparer}
import pl.touk.nussknacker.engine.lite.{InterpreterTestRunner, ScenarioInterpreterFactory, TestRunner}
import pl.touk.nussknacker.engine.requestresponse.api.RequestResponseSource
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.OutputSchemaProperty
import pl.touk.nussknacker.engine.requestresponse.metrics.InvocationMetrics
import pl.touk.nussknacker.engine.requestresponse.openapi.{
  OApiInfo,
  PathOpenApiDefinitionGenerator,
  RequestResponseOpenApiGenerator
}
import pl.touk.nussknacker.engine.resultcollector.ResultCollector

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

/*
  This is request-response-specific part of engine:
  - Future as effects
  - only one source, simple one input variable
  - if there is one error we fail whole computation
  - handling OpenAPI definition
 */
object RequestResponseInterpreter {

  def apply[Effect[_]: Monad: InterpreterShape: CapabilityTransformer](
      process: CanonicalProcess,
      processVersion: ProcessVersion,
      context: LiteEngineRuntimeContextPreparer,
      modelData: ModelData,
      additionalListeners: List[ProcessListener],
      resultCollector: ResultCollector,
      componentUseCase: ComponentUseCase
  )(
      implicit ec: ExecutionContext
  ): Validated[NonEmptyList[ProcessCompilationError], RequestResponseScenarioInterpreter[Effect]] = {
    ScenarioInterpreterFactory
      .createInterpreter[Effect, Any, AnyRef](
        process,
        JobData(process.metaData, processVersion),
        modelData,
        additionalListeners,
        resultCollector,
        componentUseCase
      )
      .map(new RequestResponseScenarioInterpreter(context.prepare(JobData(process.metaData, processVersion)), _))
  }

  // TODO: Some smarter type in Input than Context?
  class RequestResponseScenarioInterpreter[Effect[_]: Monad](
      val context: LiteEngineRuntimeContext,
      statelessScenarioInterpreter: ScenarioInterpreterWithLifecycle[Effect, Any, AnyRef]
  ) extends PathOpenApiDefinitionGenerator
      with AutoCloseable {

    private lazy val outputSchemaString: Option[String] =
      context.jobData.metaData.additionalFields.properties.get(OutputSchemaProperty)

    val scenarioName: ProcessName = context.jobData.metaData.name

    val sinkTypes: Map[NodeId, typing.TypingResult] = statelessScenarioInterpreter.sinkTypes

    private val invocationMetrics = new InvocationMetrics(context)

    val (sourceId, source) = statelessScenarioInterpreter.sources.toList match {
      case Nil                       => throw new IllegalArgumentException("No source found")
      case (sourceId, source) :: Nil => (sourceId, source.asInstanceOf[RequestResponseSource[Any]])
      case more => throw new IllegalArgumentException(s"More than one source for request-response: ${more.map(_._1)}")
    }

    private def invoke(input: Any): Effect[ValidatedNel[ErrorType, List[EndResult[AnyRef]]]] = {
      val inputBatch = ScenarioInputBatch((sourceId -> input) :: Nil)
      statelessScenarioInterpreter.invoke(inputBatch).map { case WriterT((errors, results)) =>
        NonEmptyList.fromList(errors).map(Invalid(_)).getOrElse(Valid(results))
      }
    }

    def invokeToOutput(input: Any): Effect[ValidatedNel[ErrorType, List[Any]]] = invocationMetrics.measureTime {
      invoke(input).map(_.map(_.map(_.result)))
    }

    def open(): Unit = statelessScenarioInterpreter.open(context)

    def close(): Unit = {
      statelessScenarioInterpreter.close()
      context.close()
    }

    /*
     * TODO : move inputSchema and outputSchema to one place
     * It is better to have both schemas in one place (properties or some new/custom place)
     *  */
    def getSchemaOutputProperty: Json = {
      outputSchemaString match {
        case None                  => Map("type" -> "object".asJson, "properties" -> Json.Null).asJson
        case Some(outputSchemaStr) => CirceUtil.decodeJsonUnsafe[Json](outputSchemaStr, "Provided json is not valid")
      }
    }

    def generateInfoOpenApiDefinitionPart(): OApiInfo = OApiInfo(
      title = scenarioName.value,
      version = context.jobData.processVersion.versionId.value.toString,
      description = context.jobData.metaData.additionalFields.description
    )

    override def generatePathOpenApiDefinitionPart(): Option[Json] = {
      for {
        sourceDefinition <- source.openApiDefinition
        responseDefinition = getSchemaOutputProperty
      } yield {
        // TODO: remove cyclic dependency: RequestResponseOpenApiGenerator -> RequestResponseScenarioInterpreter -> RequestResponseOpenApiGenerator
        RequestResponseOpenApiGenerator.generateScenarioDefinition(
          scenarioName.value,
          scenarioName.value,
          sourceDefinition.description,
          sourceDefinition.tags,
          sourceDefinition.definition,
          responseDefinition
        )
      }
    }

  }

  def testRunner[Effect[_]: Monad: InterpreterShape: CapabilityTransformer: EffectUnwrapper]: TestRunner =
    new InterpreterTestRunner[Effect, Context, AnyRef]

}
