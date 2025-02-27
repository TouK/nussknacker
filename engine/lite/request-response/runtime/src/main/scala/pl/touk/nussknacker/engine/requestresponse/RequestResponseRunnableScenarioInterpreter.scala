package pl.touk.nussknacker.engine.requestresponse

import akka.http.scaladsl.server.{Directives, Route}
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.{RunnableScenarioInterpreter, TaskStatus}
import pl.touk.nussknacker.engine.lite.TaskStatus.TaskStatus
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter.RequestResponseScenarioInterpreter
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.{ComponentUseCase, ModelData}

import scala.concurrent.{ExecutionContext, Future}

class RequestResponseRunnableScenarioInterpreter(
    jobData: JobData,
    parsedResolvedScenario: CanonicalProcess,
    modelData: ModelData,
    contextPreparer: LiteEngineRuntimeContextPreparer,
    requestResponseConfig: RequestResponseConfig
)(implicit ec: ExecutionContext)
    extends RunnableScenarioInterpreter
    with LazyLogging
    with Directives {

  import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._

  private var closed: Boolean = false

  private val interpreter: RequestResponseScenarioInterpreter[Future] = RequestResponseInterpreter[Future](
    parsedResolvedScenario,
    jobData.processVersion,
    contextPreparer,
    modelData,
    Nil,
    ProductionServiceInvocationCollector,
    ComponentUseCase.EngineRuntime,
  )
    .map { i =>
      i.open()
      i
    }
    .valueOr(errors => throw new IllegalArgumentException(s"Failed to compile: $errors"))

  override def run(): IO[Unit] = IO.unit

  override def status(): TaskStatus = TaskStatus.Running

  override def close(): Unit = {
    synchronized {
      if (!closed) {
        interpreter.close()
        closed = true
      }
    }
  }

  override val routes: Option[Route] = {
    Some(
      new ScenarioRoute(
        new RequestResponseHttpHandler(interpreter),
        requestResponseConfig,
        jobData.processVersion.processName
      ).combinedRoute
    )
  }

}
