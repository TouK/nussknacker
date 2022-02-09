package pl.touk.nussknacker.engine.requestresponse.management

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter
import pl.touk.nussknacker.engine.requestresponse.api.RequestResponseDeploymentData
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, ModelData, TypeSpecificInitialData}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

object RequestResponseDeploymentManager {
  def apply(modelData: ModelData, config: Config)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): RequestResponseDeploymentManager =
    new RequestResponseDeploymentManager(modelData, RequestResponseClient(config))
}

class RequestResponseDeploymentManager(modelData: ModelData, client: RequestResponseClient)(implicit ec: ExecutionContext)
  extends BaseDeploymentManager with LazyLogging {

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess,
                      savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = {
    savepointPath match {
      case Some(_) => Future.failed(new UnsupportedOperationException("Cannot make savepoint on request-response scenario"))
      case None =>
        client
          .deploy(RequestResponseDeploymentData(canonicalProcess, System.currentTimeMillis(), processVersion, deploymentData))
          .map(_ => None)
    }
  }

  override def test[T](processName: ProcessName, canonicalProcess: CanonicalProcess, testData: TestData, variableEncoder: Any => T): Future[TestResults[T]] = {
    Future{
      //TODO: shall we use StaticMethodRunner here?
      modelData.withThisAsContextClassLoader {
        val espProcess = ProcessCanonizer.uncanonizeUnsafe(canonicalProcess)
        FutureBasedRequestResponseScenarioInterpreter.testRunner.runTest(modelData, testData, espProcess, variableEncoder)
      }
    }
  }

  override def findJobStatus(processName: ProcessName): Future[Option[ProcessState]] = {
    client.findStatus(processName)
  }

  override def cancel(name: ProcessName, user: User): Future[Unit] = {
    client.cancel(name)
  }

}

class RequestResponseDeploymentManagerProvider extends DeploymentManagerProvider {

  override def createDeploymentManager(modelData: ModelData, config: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                       sttpBackend: SttpBackend[Future, Nothing, NothingT],
                                       deploymentService: ProcessingTypeDeploymentService): DeploymentManager =
    RequestResponseDeploymentManager(modelData, config)

  override def createQueryableClient(config: Config): Option[QueryableClient] = None

  override def name: String = "requestResponse"

  override def typeSpecificInitialData: TypeSpecificInitialData = TypeSpecificInitialData(RequestResponseMetaData(None))

  override def supportsSignals: Boolean = false

}
