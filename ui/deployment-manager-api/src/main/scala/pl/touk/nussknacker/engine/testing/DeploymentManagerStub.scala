package pl.touk.nussknacker.engine.testing

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.api.test.TestData
import pl.touk.nussknacker.engine.api.{ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.testmode.TestProcess
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerProvider, TypeSpecificInitialData}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class DeploymentManagerStub extends DeploymentManager with NoValidationDeploymentManager {

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] =
    Future.successful(None)

  override def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] =
    Future.successful(SavepointResult(""))

  override def cancel(name: ProcessName, user: User): Future[Unit] = Future.successful(())

  override def test[T](name: ProcessName, canonicalProcess: CanonicalProcess, testData: TestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] = ???

  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = Future.successful(None)

  override def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult] = Future.successful(SavepointResult(""))

  override def processStateDefinitionManager: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager

  override def customActions: List[CustomAction] = Nil

  override def invokeCustomAction(actionRequest: CustomActionRequest, canonicalProcess: CanonicalProcess): Future[Either[CustomActionError, CustomActionResult]] =
    Future.successful(Left(CustomActionNotImplemented(actionRequest)))

  override def close(): Unit = {}

}

//This provider can be used for testing. Override methods to implement more complex behaviour
//Provider is registered via ServiceLoader, so it can be used e.g. to run simple docker configuration
class DeploymentManagerProviderStub extends DeploymentManagerProvider {

  override def createDeploymentManager(modelData: BaseModelData, config: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                       sttpBackend: SttpBackend[Future, Nothing, NothingT],
                                       deploymentService: ProcessingTypeDeploymentService): DeploymentManager = new DeploymentManagerStub

  override def name: String = "stub"

  override def typeSpecificInitialData(config: Config): TypeSpecificInitialData = TypeSpecificInitialData(StreamMetaData())

}
