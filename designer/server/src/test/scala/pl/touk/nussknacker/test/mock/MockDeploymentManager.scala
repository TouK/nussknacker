package pl.touk.nussknacker.test.mock

import akka.actor.ActorSystem
import cats.data.Validated.valid
import cats.data.ValidatedNel
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.google.common.collect.LinkedHashMultimap
import com.typesafe.config.Config
import sttp.client3.testing.SttpBackendStub
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.deployment._
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.ScenarioStateVerificationConfig
import pl.touk.nussknacker.engine.management.{FlinkConfig, FlinkDeploymentManager, FlinkDeploymentManagerProvider}
import pl.touk.nussknacker.engine.util.loader.{DeploymentManagersClassLoader, ModelClassLoader}
import pl.touk.nussknacker.test.config.ConfigWithScalaVersion
import pl.touk.nussknacker.test.utils.domain.TestFactory
import pl.touk.nussknacker.ui.process.periodic.flink.FlinkClientStub

import java.time.Instant
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

// DEPRECATED!!! Use `WithMockableDeploymentManager` trait and `MockableDeploymentManager` instead
object MockDeploymentManager {

  def create(
      defaultProcessStateStatus: StateStatus = SimpleStateStatus.NotDeployed,
      deployedScenariosProvider: ProcessingTypeDeployedScenariosProvider =
        new ProcessingTypeDeployedScenariosProviderStub(List.empty),
      actionService: ProcessingTypeActionService = new ProcessingTypeActionServiceStub,
      scenarioActivityManager: ScenarioActivityManager = NoOpScenarioActivityManager,
      customProcessStateDefinitionManager: Option[ProcessStateDefinitionManager] = None,
  ): MockDeploymentManager = {
    new MockDeploymentManager(
      defaultProcessStateStatus,
      deployedScenariosProvider,
      actionService,
      scenarioActivityManager,
      customProcessStateDefinitionManager,
      DeploymentManagersClassLoader.create(List.empty).allocated.unsafeRunSync()(IORuntime.global)
    )(ExecutionContext.global, IORuntime.global)
  }

}

class MockDeploymentManager private (
    defaultProcessStateStatus: StateStatus = SimpleStateStatus.NotDeployed,
    deployedScenariosProvider: ProcessingTypeDeployedScenariosProvider =
      new ProcessingTypeDeployedScenariosProviderStub(List.empty),
    actionService: ProcessingTypeActionService = new ProcessingTypeActionServiceStub,
    scenarioActivityManager: ScenarioActivityManager = NoOpScenarioActivityManager,
    customProcessStateDefinitionManager: Option[ProcessStateDefinitionManager],
    deploymentManagersClassLoader: (DeploymentManagersClassLoader, IO[Unit]),
)(implicit executionContext: ExecutionContext, ioRuntime: IORuntime)
    extends FlinkDeploymentManager(
      ModelData(
        ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig),
        TestFactory.modelDependencies,
        ModelClassLoader(
          ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig).classPath,
          None,
          deploymentManagersClassLoader._1
        )
      ),
      DeploymentManagerDependencies(
        deployedScenariosProvider,
        actionService,
        scenarioActivityManager,
        executionContext,
        ioRuntime,
        ActorSystem("MockDeploymentManager"),
        SttpBackendStub.asynchronousFuture
      ),
      FlinkConfig(None, scenarioStateVerification = ScenarioStateVerificationConfig(enabled = false)),
      FlinkClientStub
    ) {

  private val deployResult = LinkedHashMultimap.create[ProcessName, Future[Option[ExternalDeploymentId]]]

  private var cancelResult: Future[Unit] = Future.successful(())

  private val managerProcessStates = new ConcurrentHashMap[ProcessName, List[StatusDetails]]

  @volatile
  private var delayBeforeStateReturn: FiniteDuration = 0 seconds

  // queue of invocations to e.g. check that deploy was already invoked in "ProcessManager"
  val deploys = new ConcurrentLinkedQueue[ProcessName]()

  // Pass correct deploymentId
  private def fallbackDeploymentId = DeploymentId(UUID.randomUUID().toString)

  override def processStateDefinitionManager: ProcessStateDefinitionManager =
    customProcessStateDefinitionManager match {
      case Some(manager) => manager
      case None          => super.processStateDefinitionManager
    }

  override def getProcessStates(
      name: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    Future {
      Thread.sleep(delayBeforeStateReturn.toMillis)
      WithDataFreshnessStatus.fresh(
        managerProcessStates.getOrDefault(name, prepareProcessState(defaultProcessStateStatus, fallbackDeploymentId))
      )
    }
  }

  private def prepareProcessState(status: StateStatus, deploymentId: DeploymentId): List[StatusDetails] =
    List(prepareProcessState(status, deploymentId, Some(ProcessVersion.empty)))

  private def prepareProcessState(
      status: StateStatus,
      deploymentId: DeploymentId,
      version: Option[ProcessVersion]
  ): StatusDetails = StatusDetails(status, Some(deploymentId), Some(ExternalDeploymentId("1")), version)

  override protected def runDeployment(command: DMRunDeploymentCommand): Future[Option[ExternalDeploymentId]] = {
    import command._
    logger.debug(s"Adding deploy for ${processVersion.processName}")
    deploys.add(processVersion.processName)

    val customActivityId = ScenarioActivityId.random
    for {
      _ <- scenarioActivityManager.saveActivity(
        ScenarioActivity.CustomAction(
          scenarioId = ScenarioId(processVersion.processId.value),
          scenarioActivityId = customActivityId,
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
      )
      externalDeploymentId <- this.synchronized {
        Option(deployResult.get(processVersion.processName))
          .map(_.toArray(Array.empty[Future[Option[ExternalDeploymentId]]]))
          .getOrElse(Array.empty)
          .lastOption
          .getOrElse(Future.successful(None))
      }
    } yield externalDeploymentId
  }

  override protected def cancelScenario(command: DMCancelScenarioCommand): Future[Unit] = cancelResult

  // We override this field, because currently, this mock bases on fallback for not defined scenarios states.
  // To make it work with state stateQueryForAllScenarios we should remove this fallback
  override def stateQueryForAllScenariosSupport: StateQueryForAllScenariosSupport = NoStateQueryForAllScenariosSupport

  override def close(): Unit = {
    super.close()
    deploymentManagersClassLoader._2.unsafeRunSync()
  }

  def withWaitForDeployFinish[T](name: ProcessName)(action: => T): T = {
    val promise = Promise[Option[ExternalDeploymentId]]()
    val future  = promise.future
    synchronized {
      deployResult.put(name, future)
    }
    try {
      action
    } finally {
      promise.complete(Try(None))
      synchronized {
        deployResult.remove(name, future)
      }
    }
  }

  def withWaitForCancelFinish[T](action: => T): T = {
    val promise = Promise[Unit]()
    try {
      cancelResult = promise.future
      action
    } finally {
      promise.complete(Try(()))
      cancelResult = Future.successful(())
    }
  }

  def withFailingDeployment[T](name: ProcessName)(action: => T): T = {
    val future = Future.failed(new RuntimeException("Failing deployment..."))
    synchronized {
      deployResult.put(name, future)
    }
    try {
      action
    } finally {
      synchronized {
        deployResult.remove(name, future)
      }
    }
  }

  def withDelayBeforeStateReturn[T](delay: FiniteDuration)(action: => T): T = {
    delayBeforeStateReturn = delay
    try {
      action
    } finally {
      delayBeforeStateReturn = 0 seconds
    }
  }

  def withProcessRunning[T](processName: ProcessName)(action: => T): T = {
    withProcessStateStatus(processName, SimpleStateStatus.Running)(action)
  }

  def withProcessFinished[T](processName: ProcessName, deploymentId: DeploymentId = fallbackDeploymentId)(
      action: => T
  ): T = {
    withProcessStateStatus(processName, SimpleStateStatus.Finished, deploymentId)(action)
  }

  def withProcessStateStatus[T](
      processName: ProcessName,
      status: StateStatus,
      deploymentId: DeploymentId = fallbackDeploymentId
  )(action: => T): T = {
    withProcessStates(processName, prepareProcessState(status, deploymentId))(action)
  }

  def withProcessStateVersion[T](processName: ProcessName, status: StateStatus, version: Option[ProcessVersion])(
      action: => T
  ): T = {
    withProcessStates(processName, List(prepareProcessState(status, fallbackDeploymentId, version)))(action)
  }

  def withEmptyProcessState[T](processName: ProcessName)(action: => T): T = {
    withProcessStates(processName, List.empty)(action)
  }

  def withProcessStates[T](processName: ProcessName, statuses: List[StatusDetails])(action: => T): T = {
    try {
      managerProcessStates.put(processName, statuses)
      action
    } finally {
      managerProcessStates.remove(processName)
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
