package pl.touk.nussknacker.engine.standalone

import cats.data.{NonEmptyList, Validated}
import io.circe.Json
import pl.touk.nussknacker.engine.Interpreter.FutureShape
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.process.RunMode
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.{Context, JobData, ProcessListener, VariableConstants}
import pl.touk.nussknacker.engine.baseengine.BaseScenarioEngine
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes.{EndResult, ErrorType, SourceId}
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.{RuntimeContext, RuntimeContextPreparer}
import pl.touk.nussknacker.engine.baseengine.metrics.InvocationMetrics
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.standalone.api.StandaloneSource
import pl.touk.nussknacker.engine.standalone.openapi.StandaloneOpenApiGenerator

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.{ExecutionContext, Future}


object StandaloneScenarioEngine extends BaseScenarioEngine[Future, AnyRef] {

  type StandaloneResultType[T] = Either[NonEmptyList[ErrorType], T]

  def apply(process: EspProcess, contextPreparer: RuntimeContextPreparer, modelData: ModelData,
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

    val (sourceId, source) = statelessScenarioInterpreter.sources.toList match {
      case Nil => throw new IllegalArgumentException("No source found")
      case (sourceId, source) :: Nil => (sourceId, source.asInstanceOf[StandaloneSource[Any]])
      case more => throw new IllegalArgumentException(s"More than one source for standalone: ${more.map(_._1)}")
    }

    override val context: RuntimeContext = statelessScenarioInterpreter.context

    private val counter = new AtomicLong(0)

    def invoke(input: Any, contextIdOpt: Option[String] = None): Future[Either[NonEmptyList[EspExceptionInfo[_<:Throwable]], List[EndResult[AnyRef]]]] = {
      val contextId = contextIdOpt.getOrElse(s"${context.processId}-${counter.getAndIncrement()}")
      measureTime {
        val ctx = Context(contextId).withVariable(VariableConstants.InputVariableName, input)
        statelessScenarioInterpreter.invoke((sourceId, ctx) :: Nil).map(_.run).map { case (errors, results) =>
          NonEmptyList.fromList(errors).map(Left(_)).getOrElse(Right(results))
        }
      }
    }

    def invokeToOutput(input: Any, contextIdOpt: Option[String] = None): Future[Either[NonEmptyList[EspExceptionInfo[_<:Throwable]], List[Any]]]
      = invoke(input, contextIdOpt).map(_.map(_.map(_.result)))

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


