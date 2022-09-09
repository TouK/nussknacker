package pl.touk.nussknacker.engine.requestresponse

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.TaskStatus.TaskStatus
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.{RunnableScenarioInterpreter, TaskStatus}
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter.RequestResponseScenarioInterpreter
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future, blocking}

class RequestResponseRunnableScenarioInterpreter(jobData: JobData,
                                                 parsedResolvedScenario: CanonicalProcess,
                                                 modelData: ModelData,
                                                 contextPreparer: LiteEngineRuntimeContextPreparer,
                                                 requestResponseConfig: RequestResponseConfig)
                                                (implicit actorSystem: ActorSystem, ec: ExecutionContext) extends RunnableScenarioInterpreter with LazyLogging with Directives {

  import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._

  private var closed: Boolean = false

  private val interpreter: RequestResponseScenarioInterpreter[Future] = RequestResponseInterpreter[Future](parsedResolvedScenario, jobData.processVersion, contextPreparer, modelData, Nil, ProductionServiceInvocationCollector, ComponentUseCase.EngineRuntime)
    .map { i =>
      i.open()
      i
    }.valueOr(errors => throw new IllegalArgumentException(s"Failed to compile: $errors"))

  override def run(): Future[Unit] = {
    val threadFactory = new BasicThreadFactory.Builder()
      .namingPattern(s"wait-until-closed")
      .build()
    // run waiting in separate thread to not exhaust main actor system thread pool
    val executionContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor(threadFactory))
    Future {
      waitUntilClosed()
    }(executionContext)
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

  override val routes: Option[Route] = {
    Some(new ScenarioRoute(new RequestResponseAkkaHttpHandler(interpreter), requestResponseConfig.definitionMetadata, jobData.processVersion.processName, "/").combinedRoute)
  }

}
