package pl.touk.nussknacker.engine.requestresponse

import cats.Monad
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel, WriterT}
import cats.implicits.toFunctorOps
import io.circe.Json
import io.circe.syntax._
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.{RunMode, Source}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.CapabilityTransformer
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{EndResult, ScenarioInputBatch, ScenarioInterpreter, SourceId}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.{LiteEngineRuntimeContext, LiteEngineRuntimeContextPreparer}
import pl.touk.nussknacker.engine.lite.{ScenarioInterpreterFactory, TestRunner}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.TestRunner.EffectUnwrapper
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.requestresponse.api.RequestResponseSource
import pl.touk.nussknacker.engine.requestresponse.openapi.RequestResponseOpenApiGenerator
import pl.touk.nussknacker.engine.requestresponse.openapi.RequestResponseOpenApiGenerator.OutputSchemaProperty

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
           (implicit ec: ExecutionContext): RequestResponseScenarioInterpreter[Effect] = {
    val interpreter = ScenarioInterpreterFactory.createInterpreter[Effect, Any, AnyRef](process, modelData, additionalListeners, resultCollector, runMode)
    new RequestResponseScenarioInterpreter(context.prepare(JobData(process.metaData, processVersion, deploymentData)), interpreter)
  }

  // TODO: Some smarter type in Input than Context?
  class RequestResponseScenarioInterpreter[Effect[_]:Monad](val context: LiteEngineRuntimeContext,
                                                            statelessScenarioInterpreter: ScenarioInterpreter[Effect, Any, AnyRef])
                                                           (implicit ec: ExecutionContext) extends AutoCloseable {

    val id: String = context.jobData.metaData.id

    // lazy because should be used after statelessScenarioInterpreter.open()
    lazy val sinkTypes: Map[NodeId, typing.TypingResult] = statelessScenarioInterpreter.sinkTypes

    lazy val (sourceId, source) = statelessScenarioInterpreter.sources.toList match {
      case Nil => throw new IllegalArgumentException("No source found")
      case (sourceId, source) :: Nil => (sourceId, source.asInstanceOf[RequestResponseSource[Any]])
      case more => throw new IllegalArgumentException(s"More than one source for request-response: ${more.map(_._1)}")
    }

    private def invoke(input: Any): Effect[ValidatedNel[ErrorType, List[EndResult[AnyRef]]]] = {
      val inputBatch = ScenarioInputBatch((sourceId -> input) :: Nil)
      statelessScenarioInterpreter.invoke(inputBatch).map { case WriterT((errors, results)) =>
        NonEmptyList.fromList(errors).map(Invalid(_)).getOrElse(Valid(results))
      }
    }

    def invokeToOutput(input: Any): Effect[ValidatedNel[ErrorType, List[Any]]] = {
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

  def testRunner[Effect[_]:InterpreterShape:CapabilityTransformer:EffectUnwrapper]: TestRunner[Effect, Context, AnyRef] = new TestRunner[Effect, Context, AnyRef]

}

