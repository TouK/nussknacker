package pl.touk.nussknacker.engine.requestresponse

import cats.Monad
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel, WriterT}
import cats.implicits.toFunctorOps
import io.circe.Json
import io.circe.syntax._
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.{RunMode, Source}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.lite.ScenarioInterpreterFactory.ScenarioInterpreterWithLifecycle
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.CapabilityTransformer
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{EndResult, ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.{LiteEngineRuntimeContext, LiteEngineRuntimeContextPreparer}
import pl.touk.nussknacker.engine.lite.{ScenarioInterpreterFactory, TestRunner}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.TestRunner.EffectUnwrapper
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.requestresponse.api.RequestResponseSource
import pl.touk.nussknacker.engine.requestresponse.openapi.RequestResponseOpenApiGenerator
import pl.touk.nussknacker.engine.requestresponse.openapi.RequestResponseOpenApiGenerator.OutputSchemaProperty

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.ExecutionContext
import scala.language.higherKinds

/*
  This is request-response-specific part of engine:
  - Future as effects
  - only one source, simple one input variable
  - if there is one error we fail whole computation
  - handling OpenAPI definition
 */
object RequestResponseEngine {

  type RequestResponseResultType[T] = ValidatedNel[ErrorType, T]

  def apply[Effect[_]:Monad:InterpreterShape:CapabilityTransformer](process: EspProcess, processVersion: ProcessVersion, deploymentData: DeploymentData, context: LiteEngineRuntimeContextPreparer, modelData: ModelData,
            additionalListeners: List[ProcessListener], resultCollector: ResultCollector, runMode: RunMode)
           (implicit ec: ExecutionContext):
  Validated[NonEmptyList[ProcessCompilationError], RequestResponseScenarioInterpreter[Effect]] = {
    ScenarioInterpreterFactory.createInterpreter[Effect, Context, AnyRef](process, modelData, additionalListeners, resultCollector, runMode)
      .map(new RequestResponseScenarioInterpreter(context.prepare(JobData(process.metaData, processVersion, deploymentData)), _))
  }

  class SourcePreparer(id: String, sources: Map[SourceId, Source]) {

    private val counter = new AtomicLong(0)

    def prepareContext(input: Any, contextIdOpt: Option[String] = None): (SourceId, Context) = {
      val contextId = contextIdOpt.getOrElse(s"$id-${counter.getAndIncrement()}")
      (sourceId, Context(contextId).withVariable(VariableConstants.InputVariableName, input))
    }

    val (sourceId, source) = sources.toList match {
      case Nil => throw new IllegalArgumentException("No source found")
      case (sourceId, source) :: Nil => (sourceId, source.asInstanceOf[RequestResponseSource[Any]])
      case more => throw new IllegalArgumentException(s"More than one source for request-response: ${more.map(_._1)}")
    }

  }

  // TODO: Some smarter type in Input than Context?
  class RequestResponseScenarioInterpreter[Effect[_]:Monad](val context: LiteEngineRuntimeContext,
                                      statelessScenarioInterpreter: ScenarioInterpreterWithLifecycle[Effect, Context, AnyRef])
                                     (implicit ec: ExecutionContext) extends AutoCloseable {

    val id: String = context.jobData.metaData.id

    val sinkTypes: Map[NodeId, typing.TypingResult] = statelessScenarioInterpreter.sinkTypes

    private val sourcePreparer = new SourcePreparer(id, statelessScenarioInterpreter.sources)

    val source: RequestResponseSource[Any] = sourcePreparer.source

    private def invoke(input: Any, contextIdOpt: Option[String] = None): Effect[ValidatedNel[ErrorType, List[EndResult[AnyRef]]]] = {
      val (sourceId, ctx) = sourcePreparer.prepareContext(input, contextIdOpt)
      val inputBatch = ScenarioInputBatch((sourceId -> ctx) :: Nil)
      statelessScenarioInterpreter.invoke(inputBatch).map { case WriterT((errors, results)) =>
        NonEmptyList.fromList(errors).map(Invalid(_)).getOrElse(Valid(results))
      }
    }

    def invokeToOutput(input: Any, contextIdOpt: Option[String] = None): Effect[ValidatedNel[ErrorType, List[Any]]] = {
      invoke(input, contextIdOpt)
        .map(_.map(_.map(_.result)))
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
      context.jobData.metaData.additionalFields.flatMap(_.properties.get(OutputSchemaProperty)) match {
        case None => Map("type" -> "object".asJson, "properties" -> Json.Null).asJson
        case Some(outputSchemaStr) => CirceUtil.decodeJsonUnsafe[Json](outputSchemaStr, "Provided json is not valid")
      }
    }

    def generateOpenApiDefinition(): Option[Json] = {
      for {
        sourceDefinition <- source.openApiDefinition
        responseDefinition = getSchemaOutputProperty
      } yield {
        RequestResponseOpenApiGenerator.generateScenarioDefinition(
          id,
          sourceDefinition.definition,
          responseDefinition,
          sourceDefinition.description,
          sourceDefinition.tags
        )
      }
    }
  }

  // TODO: Some smarter type in Input than Context?
  def testRunner[Effect[_]:InterpreterShape:CapabilityTransformer:EffectUnwrapper]: TestRunner[Effect, Context, AnyRef] = new TestRunner[Effect, Context, AnyRef] {

    override def sampleToSource(sampleData: List[AnyRef], sources: Map[SourceId, Source]): ScenarioInputBatch[Context] = {
      val preparer = new SourcePreparer("test", sources)
      ScenarioInputBatch(sampleData.map(preparer.prepareContext(_)))
    }
  }

}

