package pl.touk.nussknacker.development.manager

import akka.actor.ActorSystem
import cats.data.{Validated, ValidatedNel}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.development.manager.DevelopmentStateStatus.{
  AfterRunningActionName,
  AfterRunningStatus,
  PreparingResourcesActionName,
  PreparingResourcesStatus,
  TestActionName,
  TestStatus
}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.engine.testmode.TestProcess
import pl.touk.nussknacker.engine.{
  BaseModelData,
  DeploymentManagerDependencies,
  DeploymentManagerProvider,
  MetaDataInitializer
}

import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

class DevelopmentDeploymentManager(actorSystem: ActorSystem)
    extends DeploymentManager
    with LazyLogging
    with DeploymentManagerInconsistentStateHandlerMixIn {

  import SimpleStateStatus._

  // Use these "magic" description values to simulate deployment/validation failure
  private val descriptionForValidationFail = "validateFail"
  private val descriptionForDeploymentFail = "deployFail"

  private val MinSleepTimeSeconds = 5
  private val MaxSleepTimeSeconds = 12

  private val customActionAfterRunning = CustomAction(AfterRunningActionName, List(Running.name))
  private val customActionPreparingResources =
    CustomAction(PreparingResourcesActionName, List(NotDeployed.name, Canceled.name))
  private val customActionTest =
    CustomAction(TestActionName, Nil, icon = Some(URI.create("/assets/buttons/test_deploy.svg")))

  private val customActionStatusMapping = Map(
    customActionAfterRunning       -> AfterRunningStatus,
    customActionPreparingResources -> PreparingResourcesStatus,
    customActionTest               -> TestStatus
  )

  private val memory: TrieMap[ProcessName, StatusDetails] = TrieMap[ProcessName, StatusDetails]()
  private val random                                      = new scala.util.Random()

  implicit private class ProcessStateExpandable(processState: StatusDetails) {

    def withStateStatus(stateStatus: StateStatus): StatusDetails = {
      StatusDetails(
        stateStatus,
        processState.deploymentId,
        processState.externalDeploymentId,
        processState.version,
        Some(System.currentTimeMillis())
      )
    }

  }

  override def validate(
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess
  ): Future[Unit] = {
    if (description(canonicalProcess).contains(descriptionForValidationFail)) {
      Future.failed(new IllegalArgumentException("Scenario validation failed as description contains 'fail'"))
    } else {
      Future.successful(())
    }
  }

  private def description(canonicalProcess: CanonicalProcess) = {
    canonicalProcess.metaData.additionalFields.description
  }

  override def deploy(
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess,
      savepointPath: Option[String]
  ): Future[Option[ExternalDeploymentId]] = {
    logger.debug(s"Starting deploying scenario: ${processVersion.processName}..")
    val previous                = memory.get(processVersion.processName)
    val duringDeployStateStatus = createAndSaveProcessState(DuringDeploy, processVersion)
    val result                  = Promise[Option[ExternalDeploymentId]]()
    actorSystem.scheduler.scheduleOnce(
      sleepingTimeSeconds,
      new Runnable {
        override def run(): Unit = {
          logger.debug(s"Finished deploying scenario: ${processVersion.processName}.")
          if (description(canonicalProcess).contains(descriptionForDeploymentFail)) {
            result.complete(Failure(new RuntimeException("Failed miserably during runtime")))
            previous match {
              case Some(state) => memory.update(processVersion.processName, state)
              case None        => changeState(processVersion.processName, NotDeployed)
            }
          } else {
            result.complete(Success(duringDeployStateStatus.externalDeploymentId))
            asyncChangeState(processVersion.processName, Running)
          }
        }
      }
    )
    logger.debug(s"Finished preliminary deployment part of scenario: ${processVersion.processName}.")
    result.future
  }

  override def stop(
      name: ProcessName,
      deploymentId: DeploymentId,
      savepointDir: Option[String],
      user: User
  ): Future[SavepointResult] = {
    // TODO: stopping specific deployment
    stop(name, savepointDir, user)
  }

  override def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] = {
    logger.debug(s"Starting stopping scenario: $name..")
    asyncChangeState(name, Finished)
    logger.debug(s"Finished stopping scenario: $name.")
    Future.successful(SavepointResult(""))
  }

  override def cancel(name: ProcessName, deploymentId: DeploymentId, user: User): Future[Unit] = {
    // TODO: cancelling specific deployment
    cancel(name, user)
  }

  override def cancel(name: ProcessName, user: User): Future[Unit] = {
    logger.debug(s"Starting canceling scenario: $name..")
    changeState(name, DuringCancel)
    asyncChangeState(name, Canceled)
    logger.debug(s"Finished canceling scenario: $name.")
    Future.unit
  }

  override def test(
      name: ProcessName,
      canonicalProcess: CanonicalProcess,
      scenarioTestData: ScenarioTestData
  ): Future[TestProcess.TestResults] = ???

  override def getProcessStates(
      name: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    Future.successful(WithDataFreshnessStatus.fresh(memory.get(name).toList))
  }

  override def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult] =
    Future.successful(SavepointResult(""))

  override def processStateDefinitionManager: ProcessStateDefinitionManager =
    new DevelopmentProcessStateDefinitionManager(SimpleProcessStateDefinitionManager)

  override def customActions: List[CustomAction] = customActionStatusMapping.keys.toList

  override def invokeCustomAction(
      actionRequest: ActionRequest,
      canonicalProcess: CanonicalProcess
  ): Future[ActionResult] = {
    val processName = actionRequest.processVersion.processName
    val statusOpt = customActionStatusMapping
      .collectFirst { case (customAction, status) if customAction.name == actionRequest.name => status }

    statusOpt match {
      case Some(newStatus) =>
        asyncChangeState(processName, newStatus)
        Future.successful(ActionResult(actionRequest, s"Done ${actionRequest.name}"))
      case _ =>
        Future.failed(new NotImplementedError())
    }
  }

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
      actorSystem.scheduler.scheduleOnce(
        sleepingTimeSeconds,
        new Runnable {
          override def run(): Unit =
            changeState(name, stateStatus)
        }
      )
    }

  private def createAndSaveProcessState(stateStatus: StateStatus, processVersion: ProcessVersion): StatusDetails = {
    val processState = StatusDetails(
      stateStatus,
      None,
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

  override def createDeploymentManager(
      modelData: BaseModelData,
      dependencies: DeploymentManagerDependencies,
      config: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] =
    Validated.valid(new DevelopmentDeploymentManager(dependencies.actorSystem))

  override def metaDataInitializer(config: Config): MetaDataInitializer =
    FlinkStreamingPropertiesConfig.metaDataInitializer

  override def scenarioPropertiesConfig(config: Config): Map[String, ScenarioPropertyConfig] =
    Map(
      "deploymentManagerProperty" -> ScenarioPropertyConfig(None, None, None, None)
    ) ++ FlinkStreamingPropertiesConfig.properties

  override def name: String = "development-tests"

}
