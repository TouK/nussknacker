package pl.touk.nussknacker.engine.requestresponse

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import pl.touk.nussknacker.engine.{ModelData, RuntimeMode}
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.{RunnableScenarioInterpreter, TaskStatus}
import pl.touk.nussknacker.engine.lite.TaskStatus.TaskStatus
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter.RequestResponseScenarioInterpreter
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector

import scala.concurrent.{ExecutionContext, Future}

class RequestResponseRunnableScenarioInterpreter(
    modelData: ModelData,
    contextPreparer: LiteEngineRuntimeContextPreparer,
    parsedResolvedScenario: CanonicalProcess,
    jobData: JobData,
    nodesDeploymentData: NodesDeploymentData,
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
    nodesDeploymentData,
    contextPreparer,
    modelData,
    additionalListeners = Nil,
    ProductionServiceInvocationCollector,
    RuntimeMode.Live,
    requestResponseConfig.security
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
