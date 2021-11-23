package pl.touk.nussknacker.engine.standalone

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import io.circe.Json
import io.circe.syntax._
import pl.touk.nussknacker.engine.Interpreter.FutureShape
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.{RunMode, Source}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.baseengine.ScenarioInterpreterFactory.ScenarioInterpreterWithLifecycle
import pl.touk.nussknacker.engine.baseengine.api.commonTypes.{ErrorType, ResultType}
import pl.touk.nussknacker.engine.baseengine.api.customComponentTypes.CapabilityTransformer
import pl.touk.nussknacker.engine.baseengine.api.interpreterTypes.{EndResult, ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.{BaseEngineRuntimeContext, EngineRuntimeContextPreparer}
import pl.touk.nussknacker.engine.baseengine.capabilities.FixedCapabilityTransformer
import pl.touk.nussknacker.engine.baseengine.{ScenarioInterpreterFactory, TestRunner}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.standalone.api.StandaloneSource
import pl.touk.nussknacker.engine.standalone.metrics.InvocationMetrics
import pl.touk.nussknacker.engine.standalone.openapi.StandaloneOpenApiGenerator
import pl.touk.nussknacker.engine.standalone.openapi.StandaloneOpenApiGenerator.OutputSchemaProperty

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}


/*
  This is standalone-specific part of engine:
  - Future as effects
  - only one source, simple one input variable
  - if there is one error we fail whole computation
  - handling OpenAPI definition
 */
object StandaloneScenarioEngine {

  private implicit val capabilityTransformer: CapabilityTransformer[Future] = new FixedCapabilityTransformer[Future]()

  type StandaloneResultType[T] = ValidatedNel[ErrorType, T]

  def apply(process: EspProcess, processVersion: ProcessVersion, deploymentData: DeploymentData, context: EngineRuntimeContextPreparer, modelData: ModelData,
            additionalListeners: List[ProcessListener], resultCollector: ResultCollector, runMode: RunMode)
           (implicit ec: ExecutionContext):
  Validated[NonEmptyList[ProcessCompilationError], StandaloneScenarioInterpreter] = {
    implicit val shape: FutureShape = new FutureShape()
    ScenarioInterpreterFactory.createInterpreter[Future, AnyRef](process, modelData, additionalListeners, resultCollector, runMode)
      .map(new StandaloneScenarioInterpreter(context.prepare(JobData(process.metaData, processVersion, deploymentData)), _))
  }

  class SourcePreparer(id: String, sources: Map[SourceId, Source]) {

    private val counter = new AtomicLong(0)

    def prepareContext(input: Any, contextIdOpt: Option[String] = None): (SourceId, Context) = {
      val contextId = contextIdOpt.getOrElse(s"$id-${counter.getAndIncrement()}")
      (sourceId, Context(contextId).withVariable(VariableConstants.InputVariableName, input))
    }

    val (sourceId, source) = sources.toList match {
      case Nil => throw new IllegalArgumentException("No source found")
      case (sourceId, source) :: Nil => (sourceId, source.asInstanceOf[StandaloneSource[Any]])
      case more => throw new IllegalArgumentException(s"More than one source for standalone: ${more.map(_._1)}")
    }

  }

  class StandaloneScenarioInterpreter(val context: BaseEngineRuntimeContext,
                                      statelessScenarioInterpreter: ScenarioInterpreterWithLifecycle[Future, AnyRef])
                                     (implicit ec: ExecutionContext) extends InvocationMetrics with AutoCloseable {

    val id: String = context.jobData.metaData.id

    val sinkTypes: Map[NodeId, typing.TypingResult] = statelessScenarioInterpreter.sinkTypes

    private val sourcePreparer = new SourcePreparer(id, statelessScenarioInterpreter.sources)

    val source: StandaloneSource[Any] = sourcePreparer.source

    def invoke(input: Any, contextIdOpt: Option[String] = None): Future[ValidatedNel[ErrorType, List[EndResult[AnyRef]]]] = {
      measureTime {
        val (sourceId, ctx) = sourcePreparer.prepareContext(input, contextIdOpt)
        val inputBatch = ScenarioInputBatch((sourceId -> ctx) :: Nil)
        statelessScenarioInterpreter.invoke(inputBatch).map(_.run).map { case (errors, results) =>
          NonEmptyList.fromList(errors).map(Invalid(_)).getOrElse(Valid(results))
        }
      }
    }

    def invokeToOutput(input: Any, contextIdOpt: Option[String] = None): Future[ValidatedNel[ErrorType, List[Any]]]
      = invoke(input, contextIdOpt).map(_.map(_.map(_.result)))

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
        StandaloneOpenApiGenerator.generateScenarioDefinition(
          id,
          sourceDefinition.definition,
          responseDefinition,
          sourceDefinition.description,
          sourceDefinition.tags
        )
      }
    }
  }

  val testRunner: TestRunner[Future, AnyRef] = new TestRunner[Future, AnyRef](new FutureShape()(ExecutionContext.global), capabilityTransformer) {

    override def sampleToSource(sampleData: List[AnyRef], sources: Map[SourceId, Source]): ScenarioInputBatch = {
      val preparer = new SourcePreparer("test", sources)
      ScenarioInputBatch(sampleData.map(preparer.prepareContext(_)))
    }

    override def getResults(results: Future[ResultType[EndResult[AnyRef]]]): ResultType[EndResult[AnyRef]] =
      Await.result(results, 10 seconds)
      
  }

}


