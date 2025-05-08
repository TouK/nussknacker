package pl.touk.nussknacker.development.manager

import cats.Functor
import cats.data.Validated.valid
import cats.data.ValidatedNel
import cats.effect.{Resource, SyncIO}
import cats.effect.unsafe.IORuntime
import com.typesafe.config.Config
import io.circe.Json
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
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
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Try

class MockableDeploymentManagerProvider extends DeploymentManagerProvider {

  import net.ceedubs.ficus.Ficus._

  override def createDeploymentManager(
      modelDataProvider: BaseModelDataProvider,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      config: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] = {
    import deploymentManagerDependencies._
    valid(new MockableDeploymentManager(MockableDeploymentManager, Some(modelDataProvider)))
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

  private type ScenarioName = String

  class MockableDeploymentManager(
      configurator: MockableDeploymentManagerConfigurator,
      modelDataProviderOpt: Option[BaseModelDataProvider]
  )(
      implicit executionContext: ExecutionContext,
      ioRuntime: IORuntime
  ) extends DeploymentManager
      with ManagerSpecificScenarioActivitiesStoredByManager {

    private lazy val (miniClusterWithServicesOpt, testRunnerOpt) = {
      Functor[Option].unzip(modelDataProviderOpt.map { modelDataProvider =>
        val miniClusterWithServices = FlinkMiniClusterFactory.createMiniClusterWithServices(
          modelDataProvider.modelClassLoader,
          new Configuration,
        )
        val testRunner = new FlinkMiniClusterScenarioTestRunner(
          modelDataProvider,
          miniClusterWithServices,
          parallelism = 1,
          waitForJobIsFinishedRetryPolicy = 20.seconds.toPausePolicy
        )
        (miniClusterWithServices, testRunner)
      })
    }

    override def processStateDefinitionManager: ProcessStateDefinitionManager =
      SimpleProcessStateDefinitionManager

    override def getScenarioDeploymentsStatuses(scenarioName: ProcessName)(
        implicit freshnessPolicy: DataFreshnessPolicy
    ): Future[WithDataFreshnessStatus[List[DeploymentStatusDetails]]] = {
      val statusDetails = configurator.scenarioStatuses
        .get()
        .getOrElse(scenarioName.value, BasicStatusDetails(SimpleStateStatus.NotDeployed, version = None))
      Future.successful(
        WithDataFreshnessStatus.fresh(
          List(
            DeploymentStatusDetails(
              statusDetails.status,
              None,
              version = statusDetails.version
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
              .flatMap(configurator.deploymentResults.get().get)
              .flatMap(_.get)
          }
        case DMTestScenarioCommand(processVersion, scenario, testData) =>
          configurator.testResults
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

    override val deploymentSynchronisationSupport: DeploymentSynchronisationSupport = NoDeploymentSynchronisationSupport

    override val deploymentsStatusesQueryForAllScenariosSupport: DeploymentsStatusesQueryForAllScenariosSupport =
      new DeploymentsStatusesQueryForAllScenariosSupported {
        override def getAllScenariosDeploymentsStatuses()(
            implicit freshnessPolicy: DataFreshnessPolicy
        ): Future[WithDataFreshnessStatus[Map[ProcessName, List[DeploymentStatusDetails]]]] = {
          Future {
            WithDataFreshnessStatus.fresh(
              configurator.scenarioStatuses.get().map { case (k, v) =>
                (ProcessName(k), DeploymentStatusDetails(v.status, None, v.version) :: Nil)
              }
            )
          }
        }
      }

    override def schedulingSupport: SchedulingSupport = NoSchedulingSupport

    override def managerSpecificScenarioActivities(
        processIdWithName: ProcessIdWithName,
        after: Option[Instant],
    ): Future[List[ScenarioActivity]] =
      Future.successful(configurator.managerSpecificScenarioActivities.get())

    override def scenarioCompilationDependenciesResource: Resource[SyncIO, EngineScenarioCompilationDependencies] =
      Resource.pure(EngineScenarioCompilationDependencies.empty)

    override def close(): Unit = {
      miniClusterWithServicesOpt.foreach(_.close())
    }

  }

  class MockableDeploymentManagerConfigurator {

    private[MockableDeploymentManagerProvider] val scenarioStatuses =
      new AtomicReference[Map[ScenarioName, BasicStatusDetails]](Map.empty)
    private[MockableDeploymentManagerProvider] val testResults =
      new AtomicReference[Map[ScenarioName, TestResults[Json]]](Map.empty)
    private[MockableDeploymentManagerProvider] val deploymentResults =
      new AtomicReference[Map[DeploymentId, Try[Option[ExternalDeploymentId]]]](Map.empty)
    private[MockableDeploymentManagerProvider] val managerSpecificScenarioActivities =
      new AtomicReference[List[ScenarioActivity]](List.empty)

    def configureScenarioStatuses(scenarioStates: Map[ScenarioName, BasicStatusDetails]): Unit = {
      scenarioStatuses.set(scenarioStates)
    }

    def configureDeploymentResults(deploymentResults: Map[DeploymentId, Try[Option[ExternalDeploymentId]]]): Unit = {
      this.deploymentResults.set(deploymentResults)
    }

    def configureTestResults(scenarioTestResults: Map[ScenarioName, TestResults[Json]]): Unit = {
      testResults.set(scenarioTestResults)
    }

    def configureManagerSpecificScenarioActivities(scenarioActivities: List[ScenarioActivity]): Unit = {
      managerSpecificScenarioActivities.set(scenarioActivities)
    }

    def clean(): Unit = {
      scenarioStatuses.set(Map.empty)
      deploymentResults.set(Map.empty)
      testResults.set(Map.empty)
      managerSpecificScenarioActivities.set(List.empty)
    }

  }

  object MockableDeploymentManager extends MockableDeploymentManagerConfigurator

}

final case class BasicStatusDetails(status: StateStatus, version: Option[VersionId])
