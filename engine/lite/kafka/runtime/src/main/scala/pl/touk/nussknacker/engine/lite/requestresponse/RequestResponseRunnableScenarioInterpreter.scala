package pl.touk.nussknacker.engine.lite.requestresponse

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.TaskStatus.TaskStatus
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.{RunnableScenarioInterpreter, TaskStatus}
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter.RequestResponseScenarioInterpreter
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector

import scala.concurrent.{ExecutionContext, Future, blocking}

class RequestResponseRunnableScenarioInterpreter(jobData: JobData,
                                                 parsedResolvedScenario: EspProcess,
                                                 modelData: ModelData,
                                                 contextPreparer: LiteEngineRuntimeContextPreparer,
                                                 requestResponseConfig: RequestResponseConfig)
                                                (implicit actorSystem: ActorSystem, ec: ExecutionContext) extends RunnableScenarioInterpreter with LazyLogging {

  import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._

  private var closed: Boolean = false

  private val interpreter: RequestResponseScenarioInterpreter[Future] = RequestResponseInterpreter[Future](parsedResolvedScenario, jobData.processVersion, contextPreparer, modelData, Nil, ProductionServiceInvocationCollector, ComponentUseCase.EngineRuntime)
    .map { i =>
      i.open()
      i
    }.valueOr(errors => throw new IllegalArgumentException(s"Failed to compile: $errors"))

  override def run(): Future[Unit] = {
    Future {
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

  override def status(): TaskStatus = TaskStatus.Running

  override def close(): Unit = {
    synchronized {
      closed = true
      notify()
    }
    interpreter.close()
  }

  override def routes(): Option[Route] = {
    val path = ScenarioRoute.pathForScenario(jobData.metaData).getOrElse(parsedResolvedScenario.id) // TODO: path should be required
    val route = new ScenarioRoute(Map(path -> new RequestResponseAkkaHttpHandler(interpreter)), requestResponseConfig.definitionMetadata)
    implicit val materializer: Materializer = Materializer(actorSystem)
    Some(route.route)
  }
}
