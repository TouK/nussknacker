package pl.touk.nussknacker.engine.lite.requestresponse

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.{RunnableScenarioInterpreter, TaskStatus}
import pl.touk.nussknacker.engine.lite.TaskStatus.TaskStatus
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter.RequestResponseScenarioInterpreter
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, blocking}

class RequestResponseRunnableScenarioInterpreter(jobData: JobData,
                                                 parsedResolvedScenario: EspProcess,
                                                 modelData: ModelData,
                                                 contextPreparer: LiteEngineRuntimeContextPreparer,
                                                 requestResponseConfig: RequestResponseConfig)
                                                (implicit actorSystem: ActorSystem, ec: ExecutionContext) extends RunnableScenarioInterpreter with LazyLogging {

  import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._

  private val akkaHttpCloseTimeout = 10 seconds

  private var closed: Boolean = false

  @volatile private var interpreter: RequestResponseScenarioInterpreter[Future] = _

  @volatile private var server: ServerBinding = _

  override def run(): Future[Unit] = {
    interpreter = RequestResponseInterpreter[Future](parsedResolvedScenario, jobData.processVersion, contextPreparer, modelData, Nil, ProductionServiceInvocationCollector, ComponentUseCase.EngineRuntime)
      .valueOr(errors => throw new IllegalArgumentException(s"Failed to compile: $errors"))
    interpreter.open()
    val path = ScenarioRoute.pathForScenario(jobData.metaData).getOrElse(parsedResolvedScenario.id) // TODO: path should be required
    val route = new ScenarioRoute(Map(path -> new RequestResponseAkkaHttpHandler(interpreter)), requestResponseConfig.definitionMetadata)
    implicit val materializer: Materializer = Materializer(actorSystem)
    logger.info(s"Binding scenario route into ${requestResponseConfig.interface}:${requestResponseConfig.port}")
    Http().newServerAt(
      interface = requestResponseConfig.interface,
      port = requestResponseConfig.port
    ).bind(route.route).map { binding =>
      logger.info("Scenario route bound")
      server = binding
      waitUntilClosed()
    }
  }

  private def waitUntilClosed(): Unit = {
    blocking {
      synchronized {
        while (!closed) {
          wait()
        }
      }
    }
  }

  override def status(): TaskStatus = {
    if (server != null) TaskStatus.Running else TaskStatus.DuringDeploy
  }

  override def close(): Unit = {
    synchronized {
      closed = true
      notify()
    }
    if (server != null) Await.result(server.terminate(akkaHttpCloseTimeout), akkaHttpCloseTimeout)
    if (interpreter != null) interpreter.close()
  }

}
