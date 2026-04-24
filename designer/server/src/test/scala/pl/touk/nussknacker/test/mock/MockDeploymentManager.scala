package pl.touk.nussknacker.test.mock

import cats.data.Validated.valid
import cats.data.ValidatedNel
import cats.effect.unsafe.IORuntime
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.{JobID, JobStatus}
import org.apache.flink.configuration.Configuration
import org.apache.pekko.actor.ActorSystem
import pl.touk.nussknacker.engine._
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
import pl.touk.nussknacker.engine.management.savepoint.IdentitySavepointLocator
import pl.touk.nussknacker.test.mock.MockDeploymentManager.{sampleDeploymentId, sampleDeploymentStatusDetails}
import pl.touk.nussknacker.test.utils.domain.TestFactory
import pl.touk.nussknacker.ui.process.periodic.flink.FlinkClientStub
import sttp.client3.testing.SttpBackendStub

import java.time.Instant
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.Success
import scala.util.control.NonFatal

// DEPRECATED!!! Use `WithMockableDeploymentManager` trait and `MockableDeploymentManager` instead
class MockDeploymentManager private (
    modelData: ModelData,
    deploymentManagerDependencies: DeploymentManagerDependencies,
    customProcessStateDefinitionManager: Option[ProcessStateDefinitionManager],
    defaultDeployResult: Future[Option[ExternalDeploymentId]],
    defaultCancelResult: Future[Unit],
    closeCreatedDeps: () => Unit,
) extends FlinkDeploymentManager(
      modelData.toModelDataProvider,
      deploymentManagerDependencies,
      FlinkConfig(None, scenarioStateVerification = ScenarioStateVerificationConfig(enabled = false)),
      FlinkMiniClusterFactory.createMiniClusterWithServices(modelData.modelClassLoader, new Configuration),
      FlinkClientStub,
      FlinkScenarioJobRunnerStub,
      new IdentitySavepointLocator,
      NoLiveDataPreviewSupport,
    ) {

  import deploymentManagerDependencies._

  val deployResult = new ConcurrentHashMap[ProcessName, Future[Option[ExternalDeploymentId]]]

  @volatile
  var cancelResult: Future[Unit] = defaultCancelResult

  private[mock] def cleanCancelResult(): Unit = cancelResult = defaultCancelResult

  val managerScenariosStates = new ConcurrentHashMap[ProcessName, List[DeploymentStatusDetails]]

  @volatile
  var delayBeforeStateReturn: FiniteDuration = 0 seconds

  val successfulDeploys = new ConcurrentLinkedQueue[ProcessName]

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
        managerScenariosStates
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
    deployResult
      .getOrDefault(
        processVersion.processName,
        defaultDeployResult
      )
      .map { result =>
        logger.debug(s"Deploy successful for ${processVersion.processName}")
        successfulDeploys.add(processVersion.processName)
        result
      }
      .recoverWith { case NonFatal(ex) =>
        logger.error(s"Deploy failed for ${processVersion.processName}", ex)
        Future.failed(ex)
      }
  }

  override protected def cancelScenario(command: DMCancelScenarioCommand): Future[Unit] = cancelResult

  override def deploymentsStatusesQueryForAllScenariosSupport: DeploymentsStatusesQueryForAllScenariosSupport =
    new DeploymentsStatusesQueryForAllScenariosSupported {

      override def getAllScenariosDeploymentsStatuses()(
          implicit freshnessPolicy: DataFreshnessPolicy
      ): Future[WithDataFreshnessStatus[Map[ProcessName, List[DeploymentStatusDetails]]]] = {
        Future {
          WithDataFreshnessStatus.fresh(managerScenariosStates.asScala.toMap)
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
      config: ConfigWithUnresolvedVersion,
      customProcessStateDefinitionManager: Option[ProcessStateDefinitionManager] = None,
      defaultDeployResult: Future[Option[ExternalDeploymentId]] = Future.failed(
        new IllegalAccessException(
          "Unexpected deploy. Check if either withStubbedDeployFinish or withWaitForDeployFinish has been used."
        )
      ),
      defaultCancelResult: Future[Unit] =
        Future.failed(new IllegalAccessException("Unexpected cancel. Check if withWaitForCancelFinish has been used."))
  ): MockDeploymentManager = {
    val actorSystem = ActorSystem("MockDeploymentManager")
    val (deploymentManagersClassLoader, closeDeploymentManagerClassLoader) =
      DeploymentManagersClassLoaderFactory.create(List.empty).allocated.unsafeRunSync()(IORuntime.global)
    val modelData = ModelData(
      ProcessingTypeConfig.read(config),
      TestFactory.modelDependencies,
      ModelClassLoaderFactory.create(
        urls = ProcessingTypeConfig.read(config).classPath,
        workingDirectoryOpt = None,
        deploymentManagersClassLoader = deploymentManagersClassLoader
      )
    )
    val deploymentManagerDependencies = new DeploymentManagerDependencies(
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
      defaultDeployResult,
      defaultCancelResult,
      closeCreatedDeps,
    )
  }

  private[mock] def sampleDeploymentStatusDetails(
      status: StateStatus,
      deploymentId: DeploymentId,
  ): DeploymentStatusDetails =
    DeploymentStatusDetails(status, Some(deploymentId))

  // Pass correct deploymentId
  private[mock] def sampleDeploymentId: DeploymentId = DeploymentId(UUID.randomUUID().toString)

}

object MockDeploymentManagerSyntaxSugar extends LazyLogging {

  implicit class Ops(deploymentManager: MockDeploymentManager) {

    def withStubbedDeployResult[T](name: ProcessName, result: Option[ExternalDeploymentId] = None)(action: => T): T = {
      val future = Future.successful(result)
      deploymentManager.deployResult.put(name, future)
      try {
        action
      } finally {
        deploymentManager.deployResult.remove(name, future)
      }
    }

    def withWaitForDeployFinish[T](name: ProcessName, result: Option[ExternalDeploymentId] = None)(action: => T): T = {
      val promise = Promise[Option[ExternalDeploymentId]]()
      val future  = promise.future
      deploymentManager.deployResult.put(name, future)
      try {
        action
      } finally {
        promise.complete(Success(result))
        deploymentManager.deployResult.remove(name, future)
      }
    }

    def withWaitForCancelFinish[T](action: => T): T = {
      val promise = Promise[Unit]()
      deploymentManager.cancelResult = promise.future
      try {
        action
      } finally {
        promise.complete(Success(()))
        deploymentManager.cleanCancelResult()
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

    def withScenarioStates[T](scenarioName: ProcessName, statuses: List[DeploymentStatusDetails])(action: => T): T = {
      try {
        deploymentManager.managerScenariosStates.put(scenarioName, statuses)
        action
      } finally {
        deploymentManager.managerScenariosStates.remove(scenarioName)
      }
    }

    def withScenarioRunning[T](scenarioName: ProcessName, versionId: VersionId, startedAt: Instant)(action: => T): T = {
      withScenarioStateStatus(scenarioName, SimpleStateStatus.Running(versionId, startedAt))(action)
    }

    def withScenarioFinished[T](
        scenarioName: ProcessName,
        versionId: VersionId = VersionId(1),
        deploymentId: DeploymentId = sampleDeploymentId
    )(
        action: => T
    ): T = {
      withScenarioStateStatus(scenarioName, SimpleStateStatus.Finished(versionId), deploymentId)(action)
    }

    def withScenarioStateStatus[T](
        scenarioName: ProcessName,
        status: StateStatus,
        deploymentId: DeploymentId = sampleDeploymentId
    )(action: => T): T = {
      withScenarioStates(scenarioName, List(sampleDeploymentStatusDetails(status, deploymentId)))(action)
    }

    def withScenarioStateVersion[T](scenarioName: ProcessName, status: StateStatus)(
        action: => T
    ): T = {
      withScenarioStates(scenarioName, List(sampleDeploymentStatusDetails(status, sampleDeploymentId)))(action)
    }

    def withEmptyScenarioState[T](scenarioName: ProcessName)(action: => T): T = {
      withScenarioStates(scenarioName, List.empty)(action)
    }

  }

  trait ResultStub {

    def complete(): ResultStub

    def clean(): Unit

  }

}

class MockManagerProvider(deploymentManager: DeploymentManager) extends FlinkDeploymentManagerProvider {

  override def createDeploymentManager(
      modelDataProvider: BaseModelDataProvider,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      deploymentConfig: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] =
    valid(deploymentManager)

  override def engineSetupIdentity(config: Config): Any = ()

}
