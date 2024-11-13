package pl.touk.nussknacker.test.mock

import _root_.sttp.client3.testing.SttpBackendStub
import akka.actor.ActorSystem
import cats.data.Validated.valid
import cats.data.ValidatedNel
import com.google.common.collect.LinkedHashMultimap
import com.typesafe.config.Config
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.definition.{
  NotBlankParameterValidator,
  NotNullParameterValidator,
  StringParameterEditor
}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment._
import pl.touk.nussknacker.engine.management.{FlinkDeploymentManager, FlinkStreamingDeploymentManagerProvider}
import pl.touk.nussknacker.test.config.ConfigWithScalaVersion
import pl.touk.nussknacker.test.utils.domain.TestFactory
import shapeless.syntax.typeable.typeableOps

import java.time.Instant
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

// DEPRECATED!!! Use `WithMockableDeploymentManager` trait and `MockableDeploymentManager` instead
object MockDeploymentManager {
  val savepointPath     = "savepoints/123-savepoint"
  val stopSavepointPath = "savepoints/246-stop-savepoint"
  val maxParallelism    = 10
}

class MockDeploymentManager(
    defaultProcessStateStatus: StateStatus = SimpleStateStatus.NotDeployed,
    deployedScenariosProvider: ProcessingTypeDeployedScenariosProvider =
      new ProcessingTypeDeployedScenariosProviderStub(List.empty),
    actionService: ProcessingTypeActionService = new ProcessingTypeActionServiceStub,
    scenarioActivityManager: ScenarioActivityManager = NoOpScenarioActivityManager,
) extends FlinkDeploymentManager(
      ModelData(
        ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig),
        TestFactory.modelDependencies
      ),
      DeploymentManagerDependencies(
        deployedScenariosProvider,
        actionService,
        scenarioActivityManager,
        ExecutionContext.global,
        ActorSystem("MockDeploymentManager"),
        SttpBackendStub.asynchronousFuture
      ),
      shouldVerifyBeforeDeploy = false,
      mainClassName = "UNUSED"
    ) {

  import MockDeploymentManager._

  private def prepareProcessState(status: StateStatus, deploymentId: DeploymentId): List[StatusDetails] =
    List(prepareProcessState(status, deploymentId, Some(ProcessVersion.empty)))

  private def prepareProcessState(
      status: StateStatus,
      deploymentId: DeploymentId,
      version: Option[ProcessVersion]
  ): StatusDetails =
    StatusDetails(status, Some(deploymentId), Some(ExternalDeploymentId("1")), version)

  // Pass correct deploymentId
  private def fallbackDeploymentId = DeploymentId(UUID.randomUUID().toString)

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

  override protected def waitForDuringDeployFinished(
      processName: ProcessName,
      deploymentId: ExternalDeploymentId
  ): Future[Unit] = Future.successful(())

  private val deployResult = LinkedHashMultimap.create[ProcessName, Future[Option[ExternalDeploymentId]]]

  private var cancelResult: Future[Unit] = Future.successful(())

  private val managerProcessStates = new ConcurrentHashMap[ProcessName, List[StatusDetails]]

  @volatile
  private var delayBeforeStateReturn: FiniteDuration = 0 seconds

  // queue of invocations to e.g. check that deploy was already invoked in "ProcessManager"
  val deploys = new ConcurrentLinkedQueue[ProcessName]()

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

  override protected def makeSavepoint(
      deploymentId: ExternalDeploymentId,
      savepointDir: Option[String]
  ): Future[SavepointResult] = Future.successful(SavepointResult(path = savepointPath))

  override protected def stop(
      deploymentId: ExternalDeploymentId,
      savepointDir: Option[String]
  ): Future[SavepointResult] = Future.successful(SavepointResult(path = stopSavepointPath))

  override protected def runProgram(
      processName: ProcessName,
      mainClass: String,
      args: List[String],
      savepointPath: Option[String],
      deploymentId: Option[newdeployment.DeploymentId]
  ): Future[Option[ExternalDeploymentId]] = ???

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
        actionName = ScenarioActionName("invalid-status"),
        allowedStateStatusNames = Nil
      ),
      deployment.CustomActionDefinition(
        actionName = ScenarioActionName("has-params"),
        allowedStateStatusNames = Nil,
        parameters = CustomActionParameter(
          "testParam",
          StringParameterEditor,
          NotBlankParameterValidator :: NotNullParameterValidator :: Nil
        ) :: Nil
      )
    )
  }

  override protected def processCustomAction(command: DMCustomActionCommand): Future[CustomActionResult] =
    command.actionName.value match {
      case "hello" | "invalid-status" => Future.successful(CustomActionResult("Hi"))
      case _                          => Future.failed(new NotImplementedError())
    }

  override def close(): Unit = {}

  override def cancelDeployment(command: DMCancelDeploymentCommand): Future[Unit] = Future.successful(())

  override def cancelScenario(command: DMCancelScenarioCommand): Future[Unit] = cancelResult

  override protected def cancelFlinkJob(deploymentId: ExternalDeploymentId): Future[Unit] = Future.successful(())

  override protected def checkRequiredSlotsExceedAvailableSlots(
      canonicalProcess: CanonicalProcess,
      currentlyDeployedJobsIds: List[ExternalDeploymentId]
  ): Future[Unit] =
    if (canonicalProcess.metaData.typeSpecificData
        .cast[StreamMetaData]
        .flatMap(_.parallelism)
        .exists(_ > maxParallelism)) {
      Future.failed(new IllegalArgumentException("Parallelism too large"))
    } else {
      Future.successful(())
    }

  override def deploymentSynchronisationSupport: DeploymentSynchronisationSupport = NoDeploymentSynchronisationSupport

}

class MockManagerProvider(deploymentManager: DeploymentManager = new MockDeploymentManager())
    extends FlinkStreamingDeploymentManagerProvider {

  override def createDeploymentManager(
      modelData: BaseModelData,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      deploymentConfig: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] =
    valid(deploymentManager)

  override def engineSetupIdentity(config: Config): Any = ()

}
