package pl.touk.nussknacker.development.manager

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.development.manager.DevelopmentStateStatus.{AfterRunningStatus, PreparingResourcesStatus, TestStatus}
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.{ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.testmode.TestProcess
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerProvider, TypeSpecificInitialData}
import sttp.client3.SttpBackend

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class DevelopmentDeploymentManager(actorSystem: ActorSystem)
  extends DeploymentManager with AlwaysFreshProcessState with LazyLogging {

  import SimpleStateStatus._

  //Use these "magic" description values to simulate deployment/validation failure
  private val descriptionForValidationFail = "validateFail"
  private val descriptionForDeploymentFail = "deployFail"

  private val MinSleepTimeSeconds = 5
  private val MaxSleepTimeSeconds = 12

  private val customActionAfterRunning = CustomAction(AfterRunningStatus.name, List(Running.name))
  private val customActionPreparingResources = CustomAction(PreparingResourcesStatus.name, List(NotDeployed.name, Canceled.name))
  private val customActionTest = CustomAction(TestStatus.name, Nil)

  private val customActionStatusMapping = Map(
    customActionAfterRunning -> AfterRunningStatus,
    customActionPreparingResources -> PreparingResourcesStatus,
    customActionTest -> TestStatus
  )

  private val memory: TrieMap[ProcessName, ProcessState] = TrieMap[ProcessName, ProcessState]()
  private val random = new scala.util.Random()

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

  override def validate(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess): Future[Unit] = {
    if (description(canonicalProcess).contains(descriptionForValidationFail)) {
      Future.failed(new IllegalArgumentException("Scenario validation failed as description contains 'fail'"))
    } else {
      Future.successful(())
    }
  }

  private def description(canonicalProcess: CanonicalProcess) = {
    canonicalProcess.metaData.additionalFields.flatMap(_.description)
  }

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = {
    logger.debug(s"Starting deploying scenario: ${processVersion.processName}..")
    val previous = memory.get(processVersion.processName)
    val duringDeployStateStatus = createAndSaveProcessState(DuringDeploy, processVersion)
    val result = Promise[Option[ExternalDeploymentId]]()
    actorSystem.scheduler.scheduleOnce(sleepingTimeSeconds, new Runnable {
      override def run(): Unit = {
        logger.debug(s"Finished deploying scenario: ${processVersion.processName}.")
        if (description(canonicalProcess).contains(descriptionForDeploymentFail)) {
          result.complete(Failure(new RuntimeException("Failed miserably during runtime")))
          previous match {
            case Some(state) => memory.update(processVersion.processName, state)
            case None => changeState(processVersion.processName, NotDeployed)
          }
        } else {
          result.complete(Success(duringDeployStateStatus.deploymentId))
          asyncChangeState(processVersion.processName, Running)
        }
      }
    })
    logger.debug(s"Finished preliminary deployment part of scenario: ${processVersion.processName}.")
    result.future
  }

  override def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] = {
    logger.debug(s"Starting stopping scenario: $name..")
    asyncChangeState(name, Finished)
    logger.debug(s"Finished stopping scenario: $name.")
    Future.successful(SavepointResult(""))
  }

  override def cancel(name: ProcessName, user: User): Future[Unit] = {
    logger.debug(s"Starting canceling scenario: $name..")
    changeState(name, DuringCancel)
    asyncChangeState(name, Canceled)
    logger.debug(s"Finished canceling scenario: $name.")
    Future.unit
  }

  override def test[T](name: ProcessName, canonicalProcess: CanonicalProcess, scenarioTestData: ScenarioTestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] = ???

  override def getFreshProcessState(name: ProcessName): Future[Option[ProcessState]] =
    Future.successful(memory.get(name))

  override protected def getFreshProcessState(name: ProcessName, lastAction: Option[ProcessAction]): Future[Option[ProcessState]] =
    Future.successful(memory.get(name))

  override def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult] =
    Future.successful(SavepointResult(""))

  override def processStateDefinitionManager: ProcessStateDefinitionManager =
    new DevelopmentProcessStateDefinitionManager(SimpleProcessStateDefinitionManager)

  override def customActions: List[CustomAction] = customActionStatusMapping.keys.toList

  override def invokeCustomAction(actionRequest: CustomActionRequest, canonicalProcess: CanonicalProcess): Future[Either[CustomActionError, CustomActionResult]] =
    Future.successful(
      customActions
        .find(_.name.equals(actionRequest.name))
        .map(customAction => {
          val processName = actionRequest.processVersion.processName
          val processState = memory.getOrElse(processName, createAndSaveProcessState(NotDeployed, actionRequest.processVersion))

          if (customAction.allowedStateStatusNames.contains(processState.status.name)) {
            customActionStatusMapping
              .get(customAction)
              .map { status =>
                asyncChangeState(processName, status)
                Right(CustomActionResult(actionRequest, s"Done ${actionRequest.name}"))
              }
              .getOrElse(Left(CustomActionInvalidStatus(actionRequest, processState.status.name)))
          } else {
            Left(CustomActionInvalidStatus(actionRequest, processState.status.name))
          }
        })
        .getOrElse(Left(CustomActionNotImplemented(actionRequest)))
    )

  override def close(): Unit = {}

  private def changeState(name: ProcessName, stateStatus: StateStatus): Unit =
    memory.get(name).foreach { processState =>
      val newProcessState = processState.withStateStatus(stateStatus)
      memory.update(name, newProcessState)
      logger.debug(s"Changed scenario $name state from ${processState.status.name} to ${stateStatus.name}.")
    }

  private def asyncChangeState(name: ProcessName, stateStatus: StateStatus): Unit =
    memory.get(name).foreach { processState =>
      logger.debug(s"Starting async changing state for $name from ${processState.status.name} to ${stateStatus.name}..")
      actorSystem.scheduler.scheduleOnce(sleepingTimeSeconds, new Runnable {
        override def run(): Unit =
          changeState(name, stateStatus)
      })
    }

  private def createAndSaveProcessState(stateStatus: StateStatus, processVersion: ProcessVersion): ProcessState = {
    val processState = processStateDefinitionManager.processState(
      stateStatus,
      Some(ExternalDeploymentId(UUID.randomUUID().toString)),
      version = Some(processVersion),
      startTime = Some(System.currentTimeMillis()),
    )

    memory.update(processVersion.processName, processState)
    processState
  }

  private def sleepingTimeSeconds = FiniteDuration(
    MinSleepTimeSeconds + random.nextInt(MaxSleepTimeSeconds - MinSleepTimeSeconds + 1),
    TimeUnit.SECONDS
  )
}

class DevelopmentDeploymentManagerProvider extends DeploymentManagerProvider {

  override def createDeploymentManager(modelData: BaseModelData, config: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                       sttpBackend: SttpBackend[Future, Any], deploymentService: ProcessingTypeDeploymentService): DeploymentManager =
    new DevelopmentDeploymentManager(actorSystem)

  override def typeSpecificInitialData(config: Config): TypeSpecificInitialData = TypeSpecificInitialData(StreamMetaData())

  override def additionalPropertiesConfig(config: Config): Map[String, AdditionalPropertyConfig] =
    Map("deploymentManagerProperty" -> AdditionalPropertyConfig(None, None, None, None))

  override def name: String = "development-tests"

}
