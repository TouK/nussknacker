package pl.touk.nussknacker.development.manager

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.engine.testmode.TestProcess
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerProvider, MetaDataInitializer}
import sttp.client3.SttpBackend

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class MockableDeploymentManagerProvider extends DeploymentManagerProvider {

  override def createDeploymentManager(modelData: BaseModelData, config: Config)(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any],
      deploymentService: ProcessingTypeDeploymentService
  ): DeploymentManager =
    MockableDeploymentManager

  override def metaDataInitializer(config: Config): MetaDataInitializer =
    FlinkStreamingPropertiesConfig.metaDataInitializer

  override val name: String = "mockable"
}

object MockableDeploymentManagerProvider {

  // note: At the moment this manager cannot be used in tests which are executed in parallel. It can be obviously
  //       improved, but there is no need to do it ATM.
  object MockableDeploymentManager extends DeploymentManager {

    private val processesStates = new AtomicReference[Map[ProcessName, StateStatus]](Map.empty)

    def configure(processesStates: Map[ProcessName, StateStatus]): Unit = {
      this.processesStates.set(processesStates)
    }

    def clean(): Unit = {
      this.processesStates.set(Map.empty)
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
    ): Future[TestProcess.TestResults] = ???

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
    ): Future[Either[CustomActionError, CustomActionResult]] =
      Future.successful(Left(CustomActionNotImplemented(actionRequest)))

    override def getProcessStates(name: ProcessName)(
        implicit freshnessPolicy: DataFreshnessPolicy
    ): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
      val status = processesStates.get().getOrElse(name, SimpleStateStatus.NotDeployed)
      Future.successful(WithDataFreshnessStatus(List(StatusDetails(status, None)), cached = false))
    }

    override def close(): Unit = {}
  }

}
