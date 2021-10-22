package pl.touk.nussknacker.engine.testing

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, ProcessVersion, ScenarioSpecificData, StreamMetaData}
import pl.touk.nussknacker.engine.api.deployment.{CustomAction, CustomActionError, CustomActionNotImplemented, CustomActionRequest, CustomActionResult, DeploymentData, DeploymentManager, ExternalDeploymentId, ProcessDeploymentData, ProcessState, ProcessStateDefinitionManager, SavepointResult, TestProcess, User}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, TypeSpecificDataInitializer, ModelData}
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient

import scala.concurrent.Future

class DeploymentManagerStub extends DeploymentManager {


  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, processDeploymentData: ProcessDeploymentData, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] =
    Future.successful(None)

  override def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] =
    Future.successful(SavepointResult(""))

  override def cancel(name: ProcessName, user: User): Future[Unit] = Future.successful(())

  override def test[T](name: ProcessName, json: String, testData: TestProcess.TestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] = ???

  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = Future.successful(None)

  override def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult] = Future.successful(SavepointResult(""))

  override def processStateDefinitionManager: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager

  override def customActions: List[CustomAction] = Nil

  override def invokeCustomAction(actionRequest: CustomActionRequest, processDeploymentData: ProcessDeploymentData): Future[Either[CustomActionError, CustomActionResult]] =
    Future.successful(Left(CustomActionNotImplemented(actionRequest)))

  override def close(): Unit = {}

}

//This provider can be used for testing. Override methods to implement more complex behaviour
//Provider is registered via ServiceLoader, so it can be used e.g. to run simple docker configuration
class DeploymentManagerProviderStub extends DeploymentManagerProvider {

  override def createDeploymentManager(modelData: ModelData, config: Config): DeploymentManager = new DeploymentManagerStub

  override def createQueryableClient(config: Config): Option[QueryableClient] = None

  override def name: String = "stub"

  override def typeSpecificDataInitializer: TypeSpecificDataInitializer = new TypeSpecificDataInitializer {
    override def forScenario: ScenarioSpecificData = StreamMetaData()
    override def forFragment: FragmentSpecificData = FragmentSpecificData(None)
  }

  override def supportsSignals: Boolean = false

}
