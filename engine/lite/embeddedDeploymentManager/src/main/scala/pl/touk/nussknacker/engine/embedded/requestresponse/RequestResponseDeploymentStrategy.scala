package pl.touk.nussknacker.engine.embedded.requestresponse

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.Materializer
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.embedded.{Deployment, DeploymentStrategy}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.{HttpConfig, TestRunner}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.requestresponse.{RequestResponseAkkaHttpHandler, RequestResponseConfig, ScenarioRoute, SingleScenarioRoute}
import pl.touk.nussknacker.engine.requestresponse.{FutureBasedRequestResponseScenarioInterpreter, RequestResponseInterpreter}
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object RequestResponseDeploymentStrategy {

  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def apply(config: Config)(implicit as: ActorSystem, ec: ExecutionContext): RequestResponseDeploymentStrategy = {
    new RequestResponseDeploymentStrategy(config.as[HttpConfig]("http"), config.as[RequestResponseConfig]("request-response"))
  }

}

class RequestResponseDeploymentStrategy(httpConfig: HttpConfig, config: RequestResponseConfig)(implicit as: ActorSystem, ec: ExecutionContext)
  extends DeploymentStrategy with LazyLogging {

  private val akkaHttpSetupTimeout = 10 seconds

  private val pathToScenarioRoute = TrieMap[String, SingleScenarioRoute]()

  private var server: ServerBinding = _

  override def open(modelData: ModelData, contextPreparer: LiteEngineRuntimeContextPreparer): Unit = {
    super.open(modelData, contextPreparer)
    logger.info(s"Serving request-response on ${httpConfig.port}")

    val route = new ScenarioRoute(pathToScenarioRoute)

    implicit val materializer: Materializer = Materializer(as)
    server = Await.result(
      Http().newServerAt(
        interface = httpConfig.interface,
        port = httpConfig.port
      ).bind(route.route), akkaHttpSetupTimeout)
  }

  override def close(): Unit = {
    Await.result(server.terminate(akkaHttpSetupTimeout), akkaHttpSetupTimeout)
  }


  override def onScenarioAdded(jobData: JobData,
                               parsedResolvedScenario: EspProcess)(implicit ec: ExecutionContext): Try[RequestResponseDeployment] = synchronized {
    import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._

    val interpreter = RequestResponseInterpreter[Future](parsedResolvedScenario, jobData.processVersion, contextPreparer, modelData, Nil,
      ProductionServiceInvocationCollector, ComponentUseCase.EngineRuntime)
    val interpreterWithPath = ScenarioRoute.pathForScenario(jobData.metaData).product(interpreter)
    interpreterWithPath.foreach { case (path, interpreter) =>
      pathToScenarioRoute += (path -> new SingleScenarioRoute(new RequestResponseAkkaHttpHandler(interpreter), config.definitionMetadata, jobData.processVersion.processName, path))
      interpreter.open()
    }
    interpreterWithPath
      .map { case (path, deployment) => new RequestResponseDeployment(path, deployment) }
      .fold(errors => Failure(new IllegalArgumentException(errors.toString())), Success(_))
  }

  override def testRunner(implicit ec: ExecutionContext): TestRunner = FutureBasedRequestResponseScenarioInterpreter.testRunner

  class RequestResponseDeployment(path: String, interpreter: FutureBasedRequestResponseScenarioInterpreter.InterpreterType) extends Deployment {

    override def status(): StateStatus = SimpleStateStatus.Running

    override def close(): Unit = {
      pathToScenarioRoute.remove(path)
      interpreter.close()
    }
  }


}

