package pl.touk.nussknacker.engine.standalone

import cats.data.{NonEmptyList, Validated}
import io.circe.Json
import pl.touk.nussknacker.engine.Interpreter.FutureShape
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.process.RunMode
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult, JobData, ProcessListener, VariableConstants}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.standalone.api.{BaseScenarioEngineTypes, StandaloneContext, StandaloneContextPreparer, StandaloneScenarioEngineTypes, StandaloneSource}
import pl.touk.nussknacker.engine.standalone.metrics.InvocationMetrics
import pl.touk.nussknacker.engine.standalone.openapi.StandaloneOpenApiGenerator
import pl.touk.nussknacker.engine.stateless.BaseScenarioEngine

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.{ExecutionContext, Future}


object StandaloneScenarioEngine extends BaseScenarioEngine[Future, InterpretationResult] {

  override val baseScenarioEngineTypes: BaseScenarioEngineTypes[Future, InterpretationResult] = StandaloneScenarioEngineTypes

  import baseScenarioEngineTypes._

  def apply(process: EspProcess, contextPreparer: StandaloneContextPreparer, modelData: ModelData,
            additionalListeners: List[ProcessListener], resultCollector: ResultCollector, runMode: RunMode)
           (implicit ec: ExecutionContext):
  Validated[NonEmptyList[ProcessCompilationError], StandaloneScenarioInterpreter] = {
    implicit val shape: FutureShape = new FutureShape()
    createInterpreter(process, contextPreparer, modelData, additionalListeners, resultCollector, runMode)
      .map(new StandaloneScenarioInterpreter(_))
  }

  class StandaloneScenarioInterpreter(statelessScenarioInterpreter: ScenarioInterpreter)(implicit ec: ExecutionContext) extends InvocationMetrics with AutoCloseable {

    val id: String = statelessScenarioInterpreter.id

    val sinkTypes: Map[String, typing.TypingResult] = statelessScenarioInterpreter.sinkTypes

    val (sourceId, source) = statelessScenarioInterpreter.sources match {
      case Nil => throw new IllegalArgumentException("No source found")
      case head :: Nil => (SourceId(head.id), head.obj.asInstanceOf[StandaloneSource[Any]])
      case more => throw new IllegalArgumentException(s"More than one source for standalone: ${more.map(_.id)}")
    }

    override val context: StandaloneContext = statelessScenarioInterpreter.context

    private val counter = new AtomicLong(0)

    def invoke(input: Any, contextIdOpt: Option[String] = None): Future[GenericListResultType[InterpretationResult]] = {
      val contextId = contextIdOpt.getOrElse(s"${context.processId}-${counter.getAndIncrement()}")
      measureTime {
        val ctx = Context(contextId).withVariable(VariableConstants.InputVariableName, input)
        statelessScenarioInterpreter.invokeToResult((sourceId, ctx) :: Nil)
      }
    }

    def invokeToOutput(input: Any, contextIdOpt: Option[String] = None): Future[GenericListResultType[Any]]
      = invoke(input, contextIdOpt).map(_.map(_.map(_.output)))

    def open(jobData: JobData): Unit = statelessScenarioInterpreter.open(jobData)

    def close(): Unit = statelessScenarioInterpreter.close()

    /*
    * TODO: responseDefinition
    * We should somehow resolve returning type of sinks, which can be variant an sometimes can depend on external data source.
    * I think we should generate openApi response definition for 'sinks with schema' (to be done) only.
    * That way we can ensure that we are generating expected response.
    * */
    def generateOpenApiDefinition(): Option[Json] = {
      for {
        sourceDefinition <- source.openApiDefinition
        responseDefinition = Json.Null
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
}


