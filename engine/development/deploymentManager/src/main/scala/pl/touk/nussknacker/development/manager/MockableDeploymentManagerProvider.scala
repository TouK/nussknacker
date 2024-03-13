package pl.touk.nussknacker.development.manager

import cats.data.Validated.valid
import cats.data.ValidatedNel
import com.typesafe.config.Config
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.engine.testmode.TestProcess
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.{
  BaseModelData,
  DeploymentManagerDependencies,
  DeploymentManagerProvider,
  MetaDataInitializer
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
  object MockableDeploymentManager extends DeploymentManager {

    private val scenarioStatuses = new AtomicReference[Map[ScenarioName, StateStatus]](Map.empty)
    private val testResults      = new AtomicReference[Map[ScenarioName, TestResults]](Map.empty)

    def configure(scenarioStates: Map[ScenarioName, StateStatus]): Unit = {
      this.scenarioStatuses.set(scenarioStates)
    }

    def configureTestResults(scenarioTestResults: Map[ScenarioName, TestResults]): Unit = {
      this.testResults.set(scenarioTestResults)
    }

    def clean(): Unit = {
      this.scenarioStatuses.set(Map.empty)
      this.testResults.set(Map.empty)
    }

    override def validate(
        processVersion: ProcessVersion,
        deploymentData: DeploymentData,
        canonicalProcess: CanonicalProcess
    ): Future[Unit] =
      Future.successful(())

    override def deploy(
        processVersion: ProcessVersion,
        deploymentData: DeploymentData,
        canonicalProcess: CanonicalProcess,
        savepointPath: Option[String]
    ): Future[Option[ExternalDeploymentId]] =
      Future.successful(None)

    override def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] =
      Future.successful(SavepointResult(""))

    override def stop(
        name: ProcessName,
        deploymentId: DeploymentId,
        savepointDir: Option[String],
        user: User
    ): Future[SavepointResult] =
      Future.successful(SavepointResult(""))

    override def cancel(name: ProcessName, user: User): Future[Unit] =
      Future.successful(())

    override def cancel(name: ProcessName, deploymentId: DeploymentId, user: User): Future[Unit] =
      Future.successful(())

    override def test(
        name: ProcessName,
        canonicalProcess: CanonicalProcess,
        scenarioTestData: ScenarioTestData
    ): Future[TestProcess.TestResults] = Future.successful {
      testResults
        .get()
        .getOrElse(
          name.value,
          throw new IllegalArgumentException(s"Tests results not mocked for scenario [${name.value}]")
        )
    }

    override def resolve(
        idWithName: ProcessIdWithName,
        statusDetails: List[StatusDetails],
        lastStateAction: Option[ProcessAction]
    ): Future[ProcessState] = {
      Future.successful(processStateDefinitionManager.processState(statusDetails.head))
    }

    override def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult] =
      Future.successful(SavepointResult(""))

    override def processStateDefinitionManager: ProcessStateDefinitionManager =
      SimpleProcessStateDefinitionManager

    override def customActions: List[CustomAction] = Nil

    override def invokeCustomAction(
        actionRequest: CustomActionRequest,
        canonicalProcess: CanonicalProcess
    ): Future[CustomActionResult] =
      Future.failed(new NotImplementedError())

    override def getProcessStates(name: ProcessName)(
        implicit freshnessPolicy: DataFreshnessPolicy
    ): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
      val status = scenarioStatuses.get().getOrElse(name.value, SimpleStateStatus.NotDeployed)
      Future.successful(WithDataFreshnessStatus.fresh(List(StatusDetails(status, None))))
    }

    override def close(): Unit = {}
  }

}
