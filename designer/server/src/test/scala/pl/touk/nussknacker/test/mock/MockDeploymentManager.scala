package pl.touk.nussknacker.test.mock

import cats.data.Validated.valid
import cats.data.ValidatedNel
import cats.effect.unsafe.IORuntime
import com.typesafe.config.Config
import org.apache.flink.api.common.{JobID, JobStatus}
import org.apache.flink.configuration.Configuration
import org.apache.pekko.actor.ActorSystem
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.classloader.{DeploymentManagersClassLoaderFactory, ModelClassLoaderFactory}
import pl.touk.nussknacker.engine.deployment._
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.ScenarioStateVerificationConfig
import pl.touk.nussknacker.engine.management.{FlinkConfig, FlinkDeploymentManager, FlinkDeploymentManagerProvider}
import pl.touk.nussknacker.engine.management.jobrunner.FlinkScenarioJobRunner
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.{JobOverview, JobTasksOverview}
import pl.touk.nussknacker.test.config.ConfigWithScalaVersion
import pl.touk.nussknacker.test.mock.MockDeploymentManager.{
  defaultCancelResult,
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
import scala.util.{Success, Try}

// DEPRECATED!!! Use `WithMockableDeploymentManager` trait and `MockableDeploymentManager` instead
class MockDeploymentManager private (
    modelData: ModelData,
    deploymentManagerDependencies: DeploymentManagerDependencies,
    customProcessStateDefinitionManager: Option[ProcessStateDefinitionManager],
    closeCreatedDeps: () => Unit,
) extends FlinkDeploymentManager(
      modelData.toModelDataProvider,
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

  private val defaultDeployResult = Future.successful(
    None
  ) // Future.failed(new IllegalAccessException("Unexpected deploy. Check if withWaitForDeployFinish has been used."))

  val deployResult = new ConcurrentHashMap[ProcessName, Future[Option[ExternalDeploymentId]]]

  @volatile
  var cancelResult: Future[Unit] = defaultCancelResult

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
          .getOrDefault(scenarioName, List.empty)
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

    deployResult.getOrDefault(
      processVersion.processName,
      defaultDeployResult
    )
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

  private[mock] val defaultCancelResult =
    Future.successful(())
//    Future.failed(new IllegalAccessException("Unexpected cancel. Check if withWaitForCancelFinish has been used."))

  def create(
      deployedScenariosProvider: ProcessingTypeDeployedScenariosProvider =
        new ProcessingTypeDeployedScenariosProviderStub(List.empty),
      customProcessStateDefinitionManager: Option[ProcessStateDefinitionManager] = None,
  ): MockDeploymentManager = {
    val actorSystem = ActorSystem("MockDeploymentManager")
    val (deploymentManagersClassLoader, closeDeploymentManagerClassLoader) =
      DeploymentManagersClassLoaderFactory.create(List.empty).allocated.unsafeRunSync()(IORuntime.global)
    val modelData = ModelData(
      ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig),
      TestFactory.modelDependencies,
      ModelClassLoaderFactory.create(
        ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig).classPath,
        None,
        deploymentManagersClassLoader
      )
    )
    val deploymentManagerDependencies = new DeploymentManagerDependencies(
      deployedScenariosProvider,
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

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit class Ops(deploymentManager: MockDeploymentManager) {

    def withWaitForDeployFinish[T](name: ProcessName)(action: => T): T = {
      val resultStub = stubDeployResult(name)
      try {
        action
      } finally {
        resultStub.complete()
        resultStub.clean()
      }
    }

    def stubDeployResult[T](name: ProcessName): ResultStub = {
      val promise = Promise[Option[ExternalDeploymentId]]()
      deploymentManager.deployResult.put(name, promise.future)
      new ResultStub {
        override def complete(): ResultStub = {
          promise.complete(Success(None))
          this
        }
        override def clean(): Unit = deploymentManager.deployResult.remove(name, promise.future)
      }
    }

    def withWaitForCancelFinish[T](action: => T): T = {
      val resultStub = stubCancelResult()
      try {
        action
      } finally {
        resultStub.complete()
        resultStub.clean()
      }
    }

    def stubCancelResult(): ResultStub = {
      val promise = Promise[Unit]()
      deploymentManager.cancelResult = Future.successful(())
      new ResultStub {
        override def complete(): ResultStub = {
          promise.complete(Success(()))
          this
        }
        override def clean(): Unit = cleanCancelResult()
      }
    }

    def cleanCancelResult(): Unit = {
      deploymentManager.cancelResult = defaultCancelResult
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

  trait ResultStub {

    def complete(): ResultStub

    def clean(): Unit

  }

}

class MockManagerProvider(deploymentManager: DeploymentManager = MockDeploymentManager.create())
    extends FlinkDeploymentManagerProvider {

  override def createDeploymentManager(
      modelDataProvider: BaseModelDataProvider,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      deploymentConfig: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] =
    valid(deploymentManager)

  override def engineSetupIdentity(config: Config): Any = ()

}
