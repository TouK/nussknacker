package pl.touk.nussknacker.development.manager

import cats.data.Validated.valid
import cats.data.ValidatedNel
import com.typesafe.config.Config
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.api.definition.{
  NotBlankParameterValidator,
  NotNullParameterValidator,
  StringParameterEditor
}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.deployment.{CustomActionDefinition, CustomActionParameter}
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.engine.testing.StubbingCommands
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.{
  BaseModelData,
  DeploymentManagerDependencies,
  DeploymentManagerProvider,
  MetaDataInitializer,
  deployment
}

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class MockableDeploymentManagerProvider extends DeploymentManagerProvider {

  override def createDeploymentManager(
      modelData: BaseModelData,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      config: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] =
    valid(MockableDeploymentManager)

  override def metaDataInitializer(config: Config): MetaDataInitializer =
    FlinkStreamingPropertiesConfig.metaDataInitializer

  override val name: String = "mockable"
}

object MockableDeploymentManagerProvider {

  type ScenarioName = String

  // note: At the moment this manager cannot be used in tests which are executed in parallel. It can be obviously
  //       improved, but there is no need to do it ATM.
  object MockableDeploymentManager extends DeploymentManager with StubbingCommands {

    private val scenarioStatuses = new AtomicReference[Map[ScenarioName, StateStatus]](Map.empty)
    private val testResults      = new AtomicReference[Map[ScenarioName, TestResults]](Map.empty)

    def configure(scenarioStates: Map[ScenarioName, StateStatus]): Unit = {
      scenarioStatuses.set(scenarioStates)
    }

    def configureTestResults(scenarioTestResults: Map[ScenarioName, TestResults]): Unit = {
      testResults.set(scenarioTestResults)
    }

    def clean(): Unit = {
      scenarioStatuses.set(Map.empty)
      testResults.set(Map.empty)
    }

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
          name = ScenarioActionName("hello"),
          allowedStateStatusNames = List(ProblemStateStatus.name, NotDeployed.name)
        ),
        deployment.CustomActionDefinition(
          name = ScenarioActionName("not-implemented"),
          allowedStateStatusNames = List(ProblemStateStatus.name, NotDeployed.name)
        ),
        deployment.CustomActionDefinition(
          name = ScenarioActionName("some-params-action"),
          allowedStateStatusNames = List(ProblemStateStatus.name, NotDeployed.name),
          parameters = List(
            CustomActionParameter(
              "param1",
              StringParameterEditor,
              Some(NotBlankParameterValidator :: Nil)
            )
          )
        ),
        deployment.CustomActionDefinition(name = ScenarioActionName("invalid-status"), allowedStateStatusNames = Nil)
      )
    }

    override def getProcessStates(name: ProcessName)(
        implicit freshnessPolicy: DataFreshnessPolicy
    ): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
      val status = scenarioStatuses.get().getOrElse(name.value, SimpleStateStatus.NotDeployed)
      Future.successful(WithDataFreshnessStatus.fresh(List(StatusDetails(status, None))))
    }

    override def processCommand[Result](command: ScenarioCommand[Result]): Future[Result] = {
      command match {
        case TestScenarioCommand(scenarioName, _, _) =>
          Future.successful {
            testResults
              .get()
              .getOrElse(
                scenarioName.value,
                throw new IllegalArgumentException(s"Tests results not mocked for scenario [${scenarioName.value}]")
              )
          }
        case other =>
          super.processCommand(other)
      }
    }

    override def close(): Unit = {}
  }

}
