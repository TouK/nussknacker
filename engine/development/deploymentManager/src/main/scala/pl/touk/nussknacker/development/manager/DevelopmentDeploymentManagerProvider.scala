package pl.touk.nussknacker.development.manager

import akka.actor.ActorSystem
import cats.data.{Validated, ValidatedNel}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.development.manager.DevelopmentStateStatus._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment._
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.definition.{
  DateParameterEditor,
  LiteralIntegerValidator,
  MandatoryParameterValidator,
  StringParameterEditor,
  TextareaParameterEditor
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

  private val customActionAfterRunning = CustomActionDefinition(AfterRunningActionName, List(Running.name))

  private val customActionPreparingResources =
    deployment.CustomActionDefinition(
      PreparingResourcesActionName,
      List(NotDeployed.name, Canceled.name),
      List(
        CustomActionParameter("mandatoryString", StringParameterEditor, List(MandatoryParameterValidator)),
        CustomActionParameter("paramInt", StringParameterEditor, List(LiteralIntegerValidator)),
        CustomActionParameter("paramDate", DateParameterEditor, Nil),
        CustomActionParameter("comment", TextareaParameterEditor, Nil),
      )
    )

  private val customActionTest =
    deployment.CustomActionDefinition(TestActionName, Nil, icon = Some(URI.create("/assets/buttons/test_deploy.svg")))

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

  override def processCommand[Result](command: ScenarioCommand[Result]): Future[Result] = command match {
    case ValidateScenarioCommand(_, _, canonicalProcess) =>
      if (description(canonicalProcess).contains(descriptionForValidationFail)) {
        Future.failed(new IllegalArgumentException("Scenario validation failed as description contains 'fail'"))
      } else {
        Future.successful(())
      }
    case command: RunDeploymentCommand                      => runDeployment(command)
    case StopDeploymentCommand(name, _, savepointDir, user) =>
      // TODO: stopping specific deployment
      stopScenario(StopScenarioCommand(name, savepointDir, user))
    case command: StopScenarioCommand           => stopScenario(command)
    case CancelDeploymentCommand(name, _, user) =>
      // TODO: cancelling specific deployment
      cancelScenario(CancelScenarioCommand(name, user))
    case command: CancelScenarioCommand  => cancelScenario(command)
    case command: CustomActionCommand    => invokeCustomAction(command)
    case _: MakeScenarioSavepointCommand => Future.successful(SavepointResult(""))
    case _: TestScenarioCommand          => notImplemented
  }

  private def description(canonicalProcess: CanonicalProcess) = {
    canonicalProcess.metaData.additionalFields.description
  }

  private def runDeployment(command: RunDeploymentCommand): Future[Option[ExternalDeploymentId]] = {
    import command._
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

  private def stopScenario(command: StopScenarioCommand): Future[SavepointResult] = {
    import command._
    logger.debug(s"Starting stopping scenario: $scenarioName..")
    asyncChangeState(scenarioName, Finished)
    logger.debug(s"Finished stopping scenario: $scenarioName.")
    Future.successful(SavepointResult(""))
  }

  private def cancelScenario(command: CancelScenarioCommand): Future[Unit] = {
    import command._
    logger.debug(s"Starting canceling scenario: $scenarioName..")
    changeState(scenarioName, DuringCancel)
    asyncChangeState(scenarioName, Canceled)
    logger.debug(s"Finished canceling scenario: $scenarioName.")
    Future.unit
  }

  override def getProcessStates(
      name: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    Future.successful(WithDataFreshnessStatus.fresh(memory.get(name).toList))
  }

  override def processStateDefinitionManager: ProcessStateDefinitionManager =
    new DevelopmentProcessStateDefinitionManager(SimpleProcessStateDefinitionManager)

  override def customActionsDefinitions: List[CustomActionDefinition] = customActionStatusMapping.keys.toList

  private def invokeCustomAction(command: CustomActionCommand): Future[CustomActionResult] = {
    val processName = command.processVersion.processName
    val statusOpt = customActionStatusMapping
      .collectFirst { case (customAction, status) if customAction.name == command.actionName => status }

    statusOpt match {
      case Some(newStatus) =>
        asyncChangeState(processName, newStatus)
        Future.successful(CustomActionResult(s"Done ${command.actionName}"))
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
