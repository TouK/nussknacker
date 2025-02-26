package pl.touk.nussknacker.test.mock

import akka.actor.ActorSystem
import cats.data.Validated.valid
import cats.data.ValidatedNel
import cats.effect.unsafe.IORuntime
import com.typesafe.config.Config
import org.apache.flink.api.common.{JobID, JobStatus}
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.deployment._
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.ScenarioStateVerificationConfig
import pl.touk.nussknacker.engine.management.{FlinkConfig, FlinkDeploymentManager, FlinkDeploymentManagerProvider}
import pl.touk.nussknacker.engine.management.jobrunner.FlinkScenarioJobRunner
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.{JobOverview, JobTasksOverview}
import pl.touk.nussknacker.engine.util.loader.{DeploymentManagersClassLoader, ModelClassLoader}
import pl.touk.nussknacker.test.config.ConfigWithScalaVersion
import pl.touk.nussknacker.test.mock.MockDeploymentManager.{
  sampleCustomActionActivity,
  sampleDeploymentId,
  sampleDeploymentStatusDetails
}
import pl.touk.nussknacker.test.utils.domain.TestFactory
import pl.touk.nussknacker.ui.process.periodic.flink.FlinkClientStub
import sttp.client3.testing.SttpBackendStub

import java.time.Instant
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.Try

// DEPRECATED!!! Use `WithMockableDeploymentManager` trait and `MockableDeploymentManager` instead
class MockDeploymentManager private (
    modelData: ModelData,
    deploymentManagerDependencies: DeploymentManagerDependencies,
    defaultDeploymentStatus: StateStatus,
    scenarioActivityManager: ScenarioActivityManager,
    customProcessStateDefinitionManager: Option[ProcessStateDefinitionManager],
    closeCreatedDeps: () => Unit,
) extends FlinkDeploymentManager(
      modelData,
      deploymentManagerDependencies,
      FlinkConfig(None, scenarioStateVerification = ScenarioStateVerificationConfig(enabled = false)),
      Some(
        FlinkMiniClusterFactory
          .createMiniClusterWithServices(modelData.modelClassLoader, new Configuration)
      ),
      FlinkClientStub,
      FlinkScenarioJobRunnerStub
    ) {

  import deploymentManagerDependencies._

  val deployResult = new ConcurrentHashMap[ProcessName, Future[Option[ExternalDeploymentId]]]

  @volatile
  var cancelResult: Future[Unit] = Future.successful(())

  val managerProcessStates = new ConcurrentHashMap[ProcessName, List[DeploymentStatusDetails]]

  @volatile
  var delayBeforeStateReturn: FiniteDuration = 0 seconds

  // queue of invocations to e.g. check that deploy was already invoked in "DeploymentManager"
  val deploys = new ConcurrentLinkedQueue[ProcessName]

  override def processStateDefinitionManager: ProcessStateDefinitionManager =
    customProcessStateDefinitionManager match {
      case Some(manager) => manager
      case None          => super.processStateDefinitionManager
    }

  override protected def getScenarioDeploymentsStatusesWithJobOverview(
      scenarioName: ProcessName
  )(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[List[(DeploymentStatusDetails, JobOverview)]]] = {
    Future {
      Thread.sleep(delayBeforeStateReturn.toMillis)
      WithDataFreshnessStatus.fresh(
        managerProcessStates
          .getOrDefault(
            scenarioName,
            List(sampleDeploymentStatusDetails(defaultDeploymentStatus, sampleDeploymentId))
          )
          .map { deploymentStatus =>
            val tasksOverview = JobTasksOverview(1, 0, 0, 0, 1, 0, 0, 0, 0, 0, None)
            val deploymentIdUuid =
              deploymentStatus.deploymentId.map(id => UUID.fromString(id.value)).getOrElse(UUID.randomUUID())
            val jobOverview = JobOverview(
              new JobID(deploymentIdUuid.getLeastSignificantBits, deploymentIdUuid.getLeastSignificantBits),
              "not-important",
              -1,
              -1,
              JobStatus.RUNNING.name(),
              tasksOverview
            )
            (deploymentStatus, jobOverview)
          }
      )
    }
  }

  override protected def runDeployment(command: DMRunDeploymentCommand): Future[Option[ExternalDeploymentId]] = {
    import command._
    logger.debug(s"Adding deploy for ${processVersion.processName}")
    deploys.add(processVersion.processName)

    for {
      _                    <- scenarioActivityManager.saveActivity(sampleCustomActionActivity(processVersion))
      externalDeploymentId <- deployResult.getOrDefault(processVersion.processName, Future.successful(None))
    } yield externalDeploymentId
  }

  override protected def cancelScenario(command: DMCancelScenarioCommand): Future[Unit] = cancelResult

  override def deploymentsStatusesQueryForAllScenariosSupport: DeploymentsStatusesQueryForAllScenariosSupport =
    new DeploymentsStatusesQueryForAllScenariosSupported {

      override def getAllScenariosDeploymentsStatuses()(
          implicit freshnessPolicy: DataFreshnessPolicy
      ): Future[WithDataFreshnessStatus[Map[ProcessName, List[DeploymentStatusDetails]]]] = {
        Future {
          WithDataFreshnessStatus.fresh(managerProcessStates.asScala.toMap)
        }
      }

    }

  override def close(): Unit = {
    super.close()
    closeCreatedDeps()
  }

}

// This stub won't be used because we override the whole runDeployment method
object FlinkScenarioJobRunnerStub extends FlinkScenarioJobRunner {

  override def runScenarioJob(
      command: DMRunDeploymentCommand,
      savepointPathOpt: Option[String]
  ): Future[Option[JobID]] =
    Future.failed(new IllegalAccessException("This implementation shouldn't be used"))

}

object MockDeploymentManager {

  def create(
      defaultProcessStateStatus: StateStatus = SimpleStateStatus.NotDeployed,
      deployedScenariosProvider: ProcessingTypeDeployedScenariosProvider =
        new ProcessingTypeDeployedScenariosProviderStub(List.empty),
      actionService: ProcessingTypeActionService = new ProcessingTypeActionServiceStub,
      scenarioActivityManager: ScenarioActivityManager = NoOpScenarioActivityManager,
      customProcessStateDefinitionManager: Option[ProcessStateDefinitionManager] = None,
  ): MockDeploymentManager = {
    val actorSystem = ActorSystem("MockDeploymentManager")
    val (deploymentManagersClassLoader, closeDeploymentManagerClassLoader) =
      DeploymentManagersClassLoader.create(List.empty).allocated.unsafeRunSync()(IORuntime.global)
    val modelData = ModelData(
      ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig),
      TestFactory.modelDependencies,
      ModelClassLoader(
        ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig).classPath,
        None,
        deploymentManagersClassLoader
      )
    )
    val deploymentManagerDependencies = DeploymentManagerDependencies(
      deployedScenariosProvider,
      actionService,
      scenarioActivityManager,
      ExecutionContext.global,
      IORuntime.global,
      actorSystem,
      SttpBackendStub.asynchronousFuture
    )
    def closeCreatedDeps(): Unit = {
      closeDeploymentManagerClassLoader.unsafeRunSync()(IORuntime.global)
      actorSystem.terminate()
    }
    new MockDeploymentManager(
      modelData,
      deploymentManagerDependencies,
      defaultProcessStateStatus,
      scenarioActivityManager,
      customProcessStateDefinitionManager,
      closeCreatedDeps,
    )
  }

  private[mock] def sampleDeploymentStatusDetails(
      status: StateStatus,
      deploymentId: DeploymentId,
      version: Option[VersionId] = Some(VersionId.initialVersionId)
  ): DeploymentStatusDetails =
    DeploymentStatusDetails(status, Some(deploymentId), version)

  // Pass correct deploymentId
  private[mock] def sampleDeploymentId: DeploymentId = DeploymentId(UUID.randomUUID().toString)

  private def sampleCustomActionActivity(processVersion: ProcessVersion) =
    ScenarioActivity.CustomAction(
      scenarioId = ScenarioId(processVersion.processId.value),
      scenarioActivityId = ScenarioActivityId.random,
      user = ScenarioUser.internalNuUser,
      date = Instant.now(),
      scenarioVersionId = Some(ScenarioVersionId.from(processVersion.versionId)),
      actionName = "Custom action of MockDeploymentManager just before deployment",
      comment = ScenarioComment.from(
        content = "With comment from DeploymentManager",
        lastModifiedByUserName = ScenarioUser.internalNuUser.name,
        lastModifiedAt = Instant.now()
      ),
      result = DeploymentResult.Success(Instant.now()),
    )

}

object MockDeploymentManagerSyntaxSugar {

  implicit class Ops(deploymentManager: MockDeploymentManager) {

    def withWaitForDeployFinish[T](name: ProcessName)(action: => T): T = {
      val promise = Promise[Option[ExternalDeploymentId]]()
      val future  = promise.future
      deploymentManager.deployResult.put(name, future)
      try {
        action
      } finally {
        promise.complete(Try(None))
        deploymentManager.deployResult.remove(name, future)
      }
    }

    def withWaitForCancelFinish[T](action: => T): T = {
      val promise = Promise[Unit]()
      try {
        deploymentManager.cancelResult = promise.future
        action
      } finally {
        promise.complete(Try(()))
        deploymentManager.cancelResult = Future.successful(())
      }
    }

    def withFailingDeployment[T](name: ProcessName)(action: => T): T = {
      val future = Future.failed(new RuntimeException("Failing deployment..."))
      deploymentManager.deployResult.put(name, future)
      try {
        action
      } finally {
        deploymentManager.deployResult.remove(name, future)
      }
    }

    def withDelayBeforeStateReturn[T](delay: FiniteDuration)(action: => T): T = {
      deploymentManager.delayBeforeStateReturn = delay
      try {
        action
      } finally {
        deploymentManager.delayBeforeStateReturn = 0 seconds
      }
    }

    def withProcessStates[T](processName: ProcessName, statuses: List[DeploymentStatusDetails])(action: => T): T = {
      try {
        deploymentManager.managerProcessStates.put(processName, statuses)
        action
      } finally {
        deploymentManager.managerProcessStates.remove(processName)
      }
    }

    def withProcessRunning[T](processName: ProcessName)(action: => T): T = {
      withProcessStateStatus(processName, SimpleStateStatus.Running)(action)
    }

    def withProcessFinished[T](processName: ProcessName, deploymentId: DeploymentId = sampleDeploymentId)(
        action: => T
    ): T = {
      withProcessStateStatus(processName, SimpleStateStatus.Finished, deploymentId)(action)
    }

    def withProcessStateStatus[T](
        processName: ProcessName,
        status: StateStatus,
        deploymentId: DeploymentId = sampleDeploymentId
    )(action: => T): T = {
      withProcessStates(processName, List(sampleDeploymentStatusDetails(status, deploymentId)))(action)
    }

    def withProcessStateVersion[T](processName: ProcessName, status: StateStatus, version: Option[VersionId])(
        action: => T
    ): T = {
      withProcessStates(processName, List(sampleDeploymentStatusDetails(status, sampleDeploymentId, version)))(action)
    }

    def withEmptyProcessState[T](processName: ProcessName)(action: => T): T = {
      withProcessStates(processName, List.empty)(action)
    }

  }

}

class MockManagerProvider(deploymentManager: DeploymentManager = MockDeploymentManager.create())
    extends FlinkDeploymentManagerProvider {

  override def createDeploymentManager(
      modelData: BaseModelData,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      deploymentConfig: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] =
    valid(deploymentManager)

  override def engineSetupIdentity(config: Config): Any = ()

}
