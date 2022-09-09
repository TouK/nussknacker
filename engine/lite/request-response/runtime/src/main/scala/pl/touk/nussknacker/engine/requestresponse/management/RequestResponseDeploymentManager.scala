package pl.touk.nussknacker.engine.requestresponse.management

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData.BaseModelDataExt
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.api.test.TestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter
import pl.touk.nussknacker.engine.requestresponse.deployment.RequestResponseDeploymentData
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerProvider, TypeSpecificInitialData}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

object RequestResponseDeploymentManager {
  def apply(modelData: BaseModelData, config: Config)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): RequestResponseDeploymentManager =
    new RequestResponseDeploymentManager(modelData, RequestResponseClient(config))
}

class RequestResponseDeploymentManager(modelData: BaseModelData, client: RequestResponseClient)(implicit ec: ExecutionContext)
  extends BaseDeploymentManager with NoValidationDeploymentManager with LazyLogging {

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
      modelData.asInvokableModelData.withThisAsContextClassLoader {
        val espProcess = ProcessCanonizer.uncanonizeUnsafe(canonicalProcess)
        FutureBasedRequestResponseScenarioInterpreter.testRunner.runTest(modelData.asInvokableModelData, testData, espProcess, variableEncoder)
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

  override def createDeploymentManager(modelData: BaseModelData, config: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                       sttpBackend: SttpBackend[Future, Nothing, NothingT],
                                       deploymentService: ProcessingTypeDeploymentService): DeploymentManager =
    RequestResponseDeploymentManager(modelData, config)

  override def name: String = "requestResponse"

  override def typeSpecificInitialData(config: Config): TypeSpecificInitialData = TypeSpecificInitialData(RequestResponseMetaData(None))

}
