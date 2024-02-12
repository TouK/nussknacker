package pl.touk.nussknacker.ui.api.helpers

import akka.actor.ActorSystem
import com.google.common.collect.LinkedHashMultimap
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.DesignerWideComponentId
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.management.{FlinkDeploymentManager, FlinkStreamingDeploymentManagerProvider}
import pl.touk.nussknacker.engine.{BaseModelData, ModelData, ProcessingTypeConfig}
import pl.touk.nussknacker.ui.definition.TestAdditionalUIConfigProvider
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion
import shapeless.syntax.typeable.typeableOps
import sttp.client3.SttpBackend

import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

object MockDeploymentManager {
  val savepointPath     = "savepoints/123-savepoint"
  val stopSavepointPath = "savepoints/246-stop-savepoint"
  val maxParallelism    = 10
}

class MockDeploymentManager(val defaultProcessStateStatus: StateStatus)(
    implicit deploymentService: ProcessingTypeDeploymentService
) extends FlinkDeploymentManager(
      ModelData(
        ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig),
        TestAdditionalUIConfigProvider.componentAdditionalConfigMap,
        DesignerWideComponentId.default(TestProcessingTypes.Streaming, _)
      ),
      shouldVerifyBeforeDeploy = false,
      mainClassName = "UNUSED"
    ) {

  import MockDeploymentManager._

  def this() = {
    this(SimpleStateStatus.NotDeployed)(new ProcessingTypeDeploymentServiceStub(Nil))
  }

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

  override def getFreshProcessStates(name: ProcessName): Future[List[StatusDetails]] = {
    Future {
      Thread.sleep(delayBeforeStateReturn.toMillis)
      managerProcessStates.getOrDefault(name, prepareProcessState(defaultProcessStateStatus, fallbackDeploymentId))
    }
  }

  override def deploy(
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess,
      savepoint: Option[String]
  ): Future[Option[ExternalDeploymentId]] = {
    logger.debug(s"Adding deploy for ${processVersion.processName}")
    deploys.add(processVersion.processName)
    synchronized {
      Option(deployResult.get(processVersion.processName))
        .map(_.toArray(Array.empty[Future[Option[ExternalDeploymentId]]]))
        .getOrElse(Array.empty)
        .lastOption
        .getOrElse(Future.successful(None))
    }
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
      savepointPath: Option[String]
  ): Future[Option[ExternalDeploymentId]] = ???

  override def customActions: List[CustomAction] = {
    import SimpleStateStatus._
    List(
      CustomAction(name = "hello", allowedStateStatusNames = List(ProblemStateStatus.name, NotDeployed.name)),
      CustomAction(name = "not-implemented", allowedStateStatusNames = List(ProblemStateStatus.name, NotDeployed.name)),
      CustomAction(name = "invalid-status", allowedStateStatusNames = Nil)
    )
  }

  override def invokeCustomAction(
      actionRequest: CustomActionRequest,
      canonicalProcess: CanonicalProcess
  ): Future[CustomActionResult] =
    actionRequest.name match {
      case "hello" | "invalid-status" => Future.successful(CustomActionResult(actionRequest, "Hi"))
      case _                          => Future.failed(new NotImplementedError())
    }

  override def close(): Unit = {}

  override def cancel(name: ProcessName, user: User): Future[Unit] = cancelResult

  override protected def cancel(deploymentId: ExternalDeploymentId): Future[Unit] = Future.successful(())

  override def cancel(name: ProcessName, deploymentId: DeploymentId, user: User): Future[Unit] = Future.successful(())

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

}

object MockManagerProvider extends FlinkStreamingDeploymentManagerProvider {

  override def createDeploymentManager(modelData: BaseModelData, config: Config)(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any],
      deploymentService: ProcessingTypeDeploymentService
  ): DeploymentManager =
    new MockDeploymentManager

  override def engineSetupIdentity(config: Config): Any = ()

}
