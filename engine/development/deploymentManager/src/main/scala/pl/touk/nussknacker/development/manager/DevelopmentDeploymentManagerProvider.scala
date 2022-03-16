package pl.touk.nussknacker.development.manager

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.api.test.TestData
import pl.touk.nussknacker.engine.api.{ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.testmode.TestProcess
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerProvider, TypeSpecificInitialData}
import sttp.client.{NothingT, SttpBackend}

import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class DevelopmentDeploymentManager extends DeploymentManager with LazyLogging {

  private val MinSleepTime = 5000L
  private val MaxSleepTime = 12000L

  private val memory: TrieMap[ProcessName, ProcessState] = TrieMap[ProcessName, ProcessState]()
  private val random: ThreadLocalRandom = ThreadLocalRandom.current()

  implicit private class ProcessStateExpandable(processState: ProcessState) {
    def withStateStatus(stateStatus: StateStatus): ProcessState = {
      val processStateForStateStatus = processStateDefinitionManager.processState(
        stateStatus, startTime = Some(System.currentTimeMillis())
      )

      processStateForStateStatus.copy(
        deploymentId = processState.deploymentId,
        version = processState.version
      )
    }
  }

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = {
    logger.debug(s"Starting deploying scenario: ${processVersion.processName}..")
    val duringDeployStateStatus: ProcessState = createStateStatus(SimpleStateStatus.DuringDeploy, processVersion)
    memory.update(processVersion.processName, duringDeployStateStatus)
    asyncChangeState(processVersion.processName, SimpleStateStatus.Running)
    logger.debug(s"Finished deploying scenario: ${processVersion.processName}.")
    Future.successful(duringDeployStateStatus.deploymentId)
  }

  override def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] = {
    logger.debug(s"Starting stopping scenario: $name..")
    asyncChangeState(name, SimpleStateStatus.Finished)
    logger.debug(s"Finished stopping scenario: $name.")
    Future.successful(SavepointResult(""))
  }

  override def cancel(name: ProcessName, user: User): Future[Unit] = {
    logger.debug(s"Starting canceling scenario: $name..")
    changeState(name, SimpleStateStatus.DuringCancel)
    asyncChangeState(name, SimpleStateStatus.Canceled)
    logger.debug(s"Finished canceling scenario: $name.")
    Future.unit
  }

  override def test[T](name: ProcessName, canonicalProcess: CanonicalProcess, testData: TestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] = ???

  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] =
    Future.successful(memory.get(name))

  override def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult] =
    Future.successful(SavepointResult(""))

  override def processStateDefinitionManager: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager

  override def customActions: List[CustomAction] = List(
    CustomAction("test-running", List("RUNNING")),
    CustomAction("test-canceled", List("CANCELED")),
    CustomAction("test-all", Nil),
  )

  override def invokeCustomAction(actionRequest: CustomActionRequest, canonicalProcess: CanonicalProcess): Future[Either[CustomActionError, CustomActionResult]] =
    Future.successful(
      customActions
        .find(_.name.equals(actionRequest.name))
        .map(_ => Right(CustomActionResult(actionRequest, s"Done${actionRequest.name}")))
        .getOrElse(Left(CustomActionNotImplemented(actionRequest)))
    )

  override def close(): Unit = {}

  private def changeState(name: ProcessName, stateStatus: StateStatus): Unit =
    memory.get(name).foreach(processState => {
      val newProcessState = processState.withStateStatus(stateStatus)
      memory.update(name, newProcessState)
      logger.debug(s"Changed scenario $name state from ${processState.status.name} to ${stateStatus.name}.")
    })

  private def asyncChangeState(name: ProcessName, stateStatus: StateStatus): Unit =
    memory.get(name).foreach(processState => Future {
      logger.debug(s"Starting async changing state for $name from ${processState.status.name} to ${stateStatus.name}..")
      Thread.sleep(sleepingTime)
      changeState(name, stateStatus)
    })

  private def createStateStatus(stateStatus: StateStatus, processVersion: ProcessVersion): ProcessState = {
    processStateDefinitionManager.processState(
      stateStatus,
      Some(ExternalDeploymentId(UUID.randomUUID().toString)),
      version = Some(processVersion),
      attributes = Option.empty,
      startTime = Some(System.currentTimeMillis()),
      errors = Nil
    )
  }

  private def sleepingTime = random.nextLong(MinSleepTime, MaxSleepTime)
}

class DevelopmentDeploymentManagerProvider extends DeploymentManagerProvider {

  override def createDeploymentManager(modelData: BaseModelData, config: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                       sttpBackend: SttpBackend[Future, Nothing, NothingT], deploymentService: ProcessingTypeDeploymentService): DeploymentManager =
    new DevelopmentDeploymentManager
  override def createQueryableClient(config: Config): Option[QueryableClient] = None

  override def typeSpecificInitialData: TypeSpecificInitialData = TypeSpecificInitialData(StreamMetaData())

  override def supportsSignals: Boolean = false

  override def name: String = "development-tests"

}
