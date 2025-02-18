package pl.touk.nussknacker.development.manager

import cats.data.Validated.valid
import cats.data.ValidatedNel
import cats.effect.unsafe.IORuntime
import com.typesafe.config.Config
import io.circe.Json
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.FlinkMiniClusterScenarioTestRunner
import pl.touk.nussknacker.engine.flink.minicluster.util.DurationToRetryPolicyConverterOps._
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.engine.newdeployment.DeploymentId
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class MockableDeploymentManagerProvider extends DeploymentManagerProvider {

  import net.ceedubs.ficus.Ficus._

  override def createDeploymentManager(
      modelData: BaseModelData,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      config: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] = {
    import deploymentManagerDependencies._
    valid(new MockableDeploymentManager(Some(modelData)))
  }

  override def metaDataInitializer(config: Config): MetaDataInitializer =
    FlinkStreamingPropertiesConfig.metaDataInitializer

  override def scenarioPropertiesConfig(config: Config): Map[String, ScenarioPropertyConfig] =
    FlinkStreamingPropertiesConfig.properties

  override val name: String = "mockable"

  override def engineSetupIdentity(config: Config): Any =
    config.getAs[String]("id").getOrElse("")
}

object MockableDeploymentManagerProvider {

  type ScenarioName = String

  class MockableDeploymentManager(modelDataOpt: Option[BaseModelData])(
      implicit executionContext: ExecutionContext,
      ioRuntime: IORuntime
  ) extends DeploymentManager
      with ManagerSpecificScenarioActivitiesStoredByManager {

    private lazy val miniClusterWithServicesOpt = modelDataOpt.map { modelData =>
      FlinkMiniClusterFactory.createMiniClusterWithServices(
        modelData.modelClassLoader,
        new Configuration,
        new Configuration
      )
    }

    private lazy val testRunnerOpt =
      modelDataOpt.map { modelData =>
        new FlinkMiniClusterScenarioTestRunner(
          modelData,
          miniClusterWithServicesOpt,
          parallelism = 1,
          waitForJobIsFinishedRetryPolicy = 20.seconds.toPausePolicy
        )
      }

    override def processStateDefinitionManager: ProcessStateDefinitionManager =
      SimpleProcessStateDefinitionManager

    override def getScenarioDeploymentsStatuses(scenarioName: ProcessName)(
        implicit freshnessPolicy: DataFreshnessPolicy
    ): Future[WithDataFreshnessStatus[List[DeploymentStatusDetails]]] = {
      val statusDetails = MockableDeploymentManager.scenarioStatuses
        .get()
        .getOrElse(scenarioName.value, BasicStatusDetails(SimpleStateStatus.NotDeployed, version = None))
      Future.successful(
        WithDataFreshnessStatus.fresh(
          List(
            DeploymentStatusDetails(
              statusDetails.status,
              None,
              version = statusDetails.version.map(vId => ProcessVersion.empty.copy(versionId = vId))
            )
          )
        )
      )
    }

    override def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result] = {
      command match {
        case _: DMValidateScenarioCommand => Future.successful(())
        case DMRunDeploymentCommand(_, deploymentData, _, _) =>
          Future {
            deploymentData.deploymentId.toNewDeploymentIdOpt
              .flatMap(MockableDeploymentManager.deploymentResults.get().get)
              .flatMap(_.get)
          }
        case DMTestScenarioCommand(processVersion, scenario, testData) =>
          MockableDeploymentManager.testResults
            .get()
            .get(processVersion.processName.value)
            .map(Future.successful)
            .orElse(testRunnerOpt.map(_.runTests(scenario, testData)))
            .getOrElse(
              throw new IllegalArgumentException(
                s"Tests results not mocked for scenario [${processVersion.processName.value}] and no model data provided"
              )
            )
        case _: DMCancelScenarioCommand | _: DMStopScenarioCommand | _: DMStopDeploymentCommand |
            _: DMCancelDeploymentCommand | _: DMMakeScenarioSavepointCommand | _: DMRunOffScheduleCommand =>
          notImplemented
      }
    }

    override def deploymentSynchronisationSupport: DeploymentSynchronisationSupport = NoDeploymentSynchronisationSupport

    override def deploymentsStatusesQueryForAllScenariosSupport: DeploymentsStatusesQueryForAllScenariosSupport =
      NoDeploymentsStatusesQueryForAllScenariosSupport$

    override def schedulingSupport: SchedulingSupport = NoSchedulingSupport

    override def managerSpecificScenarioActivities(
        processIdWithName: ProcessIdWithName,
        after: Option[Instant],
    ): Future[List[ScenarioActivity]] =
      Future.successful(MockableDeploymentManager.managerSpecificScenarioActivities.get())

    override def close(): Unit = {
      miniClusterWithServicesOpt.foreach(_.close())
    }

  }

  // note: At the moment this manager cannot be used in tests which are executed in parallel. It can be obviously
  //       improved, but there is no need to do it ATM.
  object MockableDeploymentManager {

    private val scenarioStatuses  = new AtomicReference[Map[ScenarioName, BasicStatusDetails]](Map.empty)
    private val testResults       = new AtomicReference[Map[ScenarioName, TestResults[Json]]](Map.empty)
    private val deploymentResults = new AtomicReference[Map[DeploymentId, Try[Option[ExternalDeploymentId]]]](Map.empty)
    private val managerSpecificScenarioActivities = new AtomicReference[List[ScenarioActivity]](List.empty)

    def configureScenarioStatuses(scenarioStates: Map[ScenarioName, BasicStatusDetails]): Unit = {
      MockableDeploymentManager.scenarioStatuses.set(scenarioStates)
    }

    def configureDeploymentResults(deploymentResults: Map[DeploymentId, Try[Option[ExternalDeploymentId]]]): Unit = {
      MockableDeploymentManager.deploymentResults.set(deploymentResults)
    }

    def configureTestResults(scenarioTestResults: Map[ScenarioName, TestResults[Json]]): Unit = {
      MockableDeploymentManager.testResults.set(scenarioTestResults)
    }

    def configureManagerSpecificScenarioActivities(scenarioActivities: List[ScenarioActivity]): Unit = {
      MockableDeploymentManager.managerSpecificScenarioActivities.set(scenarioActivities)
    }

    def clean(): Unit = {
      MockableDeploymentManager.scenarioStatuses.set(Map.empty)
      MockableDeploymentManager.deploymentResults.set(Map.empty)
      MockableDeploymentManager.testResults.set(Map.empty)
      MockableDeploymentManager.managerSpecificScenarioActivities.set(List.empty)
    }

  }

}

case class BasicStatusDetails(status: StateStatus, version: Option[VersionId])
