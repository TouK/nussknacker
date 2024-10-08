package pl.touk.nussknacker.development.manager

import cats.data.Validated.valid
import cats.data.ValidatedNel
import com.typesafe.config.Config
import io.circe.Json
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.ModelData.BaseModelDataExt
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{NotBlankParameterValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.deployment.DeploymentManagerScenarioActivityHandling.NoManagerSpecificScenarioActivities
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.deployment.{CustomActionDefinition, CustomActionParameter, ExternalDeploymentId}
import pl.touk.nussknacker.engine.management.{FlinkProcessTestRunner, FlinkStreamingPropertiesConfig}
import pl.touk.nussknacker.engine.newdeployment.DeploymentId
import pl.touk.nussknacker.engine.testing.StubbingCommands
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

class MockableDeploymentManagerProvider extends DeploymentManagerProvider {

  import net.ceedubs.ficus.Ficus._

  override def createDeploymentManager(
      modelData: BaseModelData,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      config: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] =
    valid(new MockableDeploymentManager(Some(modelData)))

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

  class MockableDeploymentManager(modelDataOpt: Option[BaseModelData]) extends DeploymentManager with StubbingCommands {

    private lazy val testRunnerOpt =
      modelDataOpt.map(modelData => new FlinkProcessTestRunner(modelData.asInvokableModelData))

    override def resolve(
        idWithName: ProcessIdWithName,
        statusDetails: List[StatusDetails],
        lastStateAction: Option[ProcessAction]
    ): Future[ProcessState] = {
      Future.successful(processStateDefinitionManager.processState(statusDetails.head))
    }

    override def processStateDefinitionManager: ProcessStateDefinitionManager =
      SimpleProcessStateDefinitionManager

    override def customActionsDefinitions: List[CustomActionDefinition] = {
      import SimpleStateStatus._
      List(
        deployment.CustomActionDefinition(
          actionName = ScenarioActionName("hello"),
          allowedStateStatusNames = List(ProblemStateStatus.name, NotDeployed.name)
        ),
        deployment.CustomActionDefinition(
          actionName = ScenarioActionName("not-implemented"),
          allowedStateStatusNames = List(ProblemStateStatus.name, NotDeployed.name)
        ),
        deployment.CustomActionDefinition(
          actionName = ScenarioActionName("some-params-action"),
          allowedStateStatusNames = List(ProblemStateStatus.name, NotDeployed.name),
          parameters = List(
            CustomActionParameter(
              "param1",
              StringParameterEditor,
              NotBlankParameterValidator :: Nil
            )
          )
        ),
        deployment.CustomActionDefinition(
          actionName = ScenarioActionName("invalid-status"),
          allowedStateStatusNames = Nil
        )
      )
    }

    override def getProcessStates(name: ProcessName)(
        implicit freshnessPolicy: DataFreshnessPolicy
    ): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
      val status = MockableDeploymentManager.scenarioStatuses.get().getOrElse(name.value, SimpleStateStatus.NotDeployed)
      Future.successful(WithDataFreshnessStatus.fresh(List(StatusDetails(status, None))))
    }

    override def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result] = {
      command match {
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
            .orElse(testRunnerOpt.map(_.test(scenario, testData)))
            .getOrElse(
              throw new IllegalArgumentException(
                s"Tests results not mocked for scenario [${processVersion.processName.value}] and no model data provided"
              )
            )
        case other =>
          super.processCommand(other)
      }
    }

    override def deploymentSynchronisationSupport: DeploymentSynchronisationSupport = NoDeploymentSynchronisationSupport

    override def scenarioActivityHandling: DeploymentManagerScenarioActivityHandling =
      NoManagerSpecificScenarioActivities

    override def close(): Unit = {}
  }

  // note: At the moment this manager cannot be used in tests which are executed in parallel. It can be obviously
  //       improved, but there is no need to do it ATM.
  object MockableDeploymentManager {

    private val scenarioStatuses  = new AtomicReference[Map[ScenarioName, StateStatus]](Map.empty)
    private val testResults       = new AtomicReference[Map[ScenarioName, TestResults[Json]]](Map.empty)
    private val deploymentResults = new AtomicReference[Map[DeploymentId, Try[Option[ExternalDeploymentId]]]](Map.empty)
    private val managerSpecificScenarioActivities = new AtomicReference[List[ScenarioActivity]](List.empty)

    def configureScenarioStatuses(scenarioStates: Map[ScenarioName, StateStatus]): Unit = {
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
