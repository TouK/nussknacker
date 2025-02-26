package pl.touk.nussknacker.development.manager

import cats.data.{Validated, ValidatedNel}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment._
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.FlinkMiniClusterScenarioTestRunner
import pl.touk.nussknacker.engine.flink.minicluster.util.DurationToRetryPolicyConverterOps._
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.collection.concurrent.TrieMap
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.{Failure, Success}

class DevelopmentDeploymentManager(dependencies: DeploymentManagerDependencies, modelData: BaseModelData)
    extends DeploymentManager
    with LazyLogging {

  import dependencies._

  import SimpleStateStatus._

  // Use these "magic" description values to simulate deployment/validation failure
  private val descriptionForValidationFail = "validateFail"
  private val descriptionForDeploymentFail = "deployFail"

  private val MinSleepTimeSeconds = 5
  private val MaxSleepTimeSeconds = 12

  private val memory: TrieMap[ProcessName, DeploymentStatusDetails] = TrieMap[ProcessName, DeploymentStatusDetails]()
  private val random                                                = new scala.util.Random()

  private val miniClusterWithServices =
    FlinkMiniClusterFactory
      .createMiniClusterWithServices(
        modelData.modelClassLoader,
        new Configuration,
      )

  private lazy val flinkTestRunner =
    new FlinkMiniClusterScenarioTestRunner(
      modelData,
      Some(miniClusterWithServices),
      parallelism = 1,
      waitForJobIsFinishedRetryPolicy = 20.seconds.toPausePolicy
    )

  override def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result] = command match {
    case DMValidateScenarioCommand(_, _, canonicalProcess, _) =>
      if (description(canonicalProcess).contains(descriptionForValidationFail)) {
        Future.failed(new IllegalArgumentException("Scenario validation failed as description contains 'fail'"))
      } else {
        Future.successful(())
      }
    case command: DMRunDeploymentCommand                      => runDeployment(command)
    case DMStopDeploymentCommand(name, _, savepointDir, user) =>
      // TODO: stopping specific deployment
      stopScenario(DMStopScenarioCommand(name, savepointDir, user))
    case command: DMStopScenarioCommand           => stopScenario(command)
    case DMCancelDeploymentCommand(name, _, user) =>
      // TODO: cancelling specific deployment
      cancelScenario(DMCancelScenarioCommand(name, user))
    case command: DMCancelScenarioCommand  => cancelScenario(command)
    case command: DMRunOffScheduleCommand  => runOffSchedule(command)
    case _: DMMakeScenarioSavepointCommand => Future.successful(SavepointResult(""))
    case DMTestScenarioCommand(_, canonicalProcess, scenarioTestData) =>
      flinkTestRunner.runTests(
        canonicalProcess,
        scenarioTestData
      ) // it's just for streaming e2e tests from file purposes
  }

  private def description(canonicalProcess: CanonicalProcess) = {
    canonicalProcess.metaData.additionalFields.description
  }

  private def runDeployment(command: DMRunDeploymentCommand): Future[Option[ExternalDeploymentId]] = {
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
            result.complete(Success(duringDeployStateStatus.deploymentId.map(_.value).map(ExternalDeploymentId(_))))
            asyncChangeState(processVersion.processName, Running)
          }
        }
      }
    )
    logger.debug(s"Finished preliminary deployment part of scenario: ${processVersion.processName}.")
    result.future
  }

  private def stopScenario(command: DMStopScenarioCommand): Future[SavepointResult] = {
    import command._
    logger.debug(s"Starting stopping scenario: $scenarioName..")
    asyncChangeState(scenarioName, Finished)
    logger.debug(s"Finished stopping scenario: $scenarioName.")
    Future.successful(SavepointResult(""))
  }

  private def cancelScenario(command: DMCancelScenarioCommand): Future[Unit] = {
    import command._
    logger.debug(s"Starting canceling scenario: $scenarioName..")
    changeState(scenarioName, DuringCancel)
    asyncChangeState(scenarioName, Canceled)
    logger.debug(s"Finished canceling scenario: $scenarioName.")
    Future.unit
  }

  override def getScenarioDeploymentsStatuses(
      scenarioName: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[DeploymentStatusDetails]]] = {
    Future.successful(WithDataFreshnessStatus.fresh(memory.get(scenarioName).toList))
  }

  override def processStateDefinitionManager: ProcessStateDefinitionManager =
    new DevelopmentProcessStateDefinitionManager(SimpleProcessStateDefinitionManager)

  private def runOffSchedule(command: DMRunOffScheduleCommand): Future[RunOffScheduleResult] = {
    notImplemented
  }

  override def close(): Unit = {
    miniClusterWithServices.close()
  }

  private def changeState(name: ProcessName, stateStatus: StateStatus): Unit =
    memory.get(name).foreach { statusDetails =>
      val newProcessState = statusDetails.copy(status = stateStatus)
      memory.update(name, newProcessState)
      logger.debug(s"Changed scenario $name state from ${statusDetails.status.name} to ${stateStatus.name}.")
    }

  private def asyncChangeState(name: ProcessName, stateStatus: StateStatus): Unit =
    memory.get(name).foreach { statusDetails =>
      logger
        .debug(s"Starting async changing state for $name from ${statusDetails.status.name} to ${stateStatus.name}..")
      actorSystem.scheduler.scheduleOnce(
        sleepingTimeSeconds,
        new Runnable {
          override def run(): Unit =
            changeState(name, stateStatus)
        }
      )
    }

  private def createAndSaveProcessState(
      stateStatus: StateStatus,
      processVersion: ProcessVersion
  ): DeploymentStatusDetails = {
    val statusDetails = DeploymentStatusDetails(
      status = stateStatus,
      deploymentId = None,
      version = Some(processVersion.versionId),
    )

    memory.update(processVersion.processName, statusDetails)
    statusDetails
  }

  private def sleepingTimeSeconds = FiniteDuration(
    MinSleepTimeSeconds + random.nextInt(MaxSleepTimeSeconds - MinSleepTimeSeconds + 1),
    TimeUnit.SECONDS
  )

  override def deploymentSynchronisationSupport: DeploymentSynchronisationSupport = NoDeploymentSynchronisationSupport

  override def deploymentsStatusesQueryForAllScenariosSupport: DeploymentsStatusesQueryForAllScenariosSupport =
    NoDeploymentsStatusesQueryForAllScenariosSupport

  override def schedulingSupport: SchedulingSupport = NoSchedulingSupport
}

class DevelopmentDeploymentManagerProvider extends DeploymentManagerProvider {

  override def createDeploymentManager(
      modelData: BaseModelData,
      dependencies: DeploymentManagerDependencies,
      config: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] =
    Validated.valid(new DevelopmentDeploymentManager(dependencies, modelData))

  override def metaDataInitializer(config: Config): MetaDataInitializer =
    FlinkStreamingPropertiesConfig.metaDataInitializer

  override def scenarioPropertiesConfig(config: Config): Map[String, ScenarioPropertyConfig] =
    Map(
      "deploymentManagerProperty" -> ScenarioPropertyConfig(None, None, None, None, None)
    ) ++ FlinkStreamingPropertiesConfig.properties

  override def name: String = "development-tests"

}
