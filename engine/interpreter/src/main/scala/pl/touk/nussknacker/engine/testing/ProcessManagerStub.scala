package pl.touk.nussknacker.engine.testing

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.{ProcessVersion, StreamMetaData, TypeSpecificData}
import pl.touk.nussknacker.engine.api.deployment.{CustomAction, CustomActionError, CustomActionNotImplemented, CustomActionRequest, CustomActionResult, ProcessDeploymentData, ProcessManager, ProcessState, ProcessStateDefinitionManager, SavepointResult, TestProcess, User}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.{ModelData, ProcessManagerProvider}
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient

import scala.concurrent.Future

class ProcessManagerStub extends ProcessManager {

  override def deploy(processId: ProcessVersion, processDeploymentData: ProcessDeploymentData, savepointPath: Option[String], user: User): Future[Unit] =
    Future.successful(())

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

class ProcessManagerProviderStub extends ProcessManagerProvider {

  override def createProcessManager(modelData: ModelData, config: Config): ProcessManager = new ProcessManagerStub

  override def createQueryableClient(config: Config): Option[QueryableClient] = None

  override def name: String = "stub"

  override def emptyProcessMetadata(isSubprocess: Boolean): TypeSpecificData = StreamMetaData()

  override def supportsSignals: Boolean = false

}
