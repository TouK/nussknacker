package pl.touk.nussknacker.ui.api.helpers

import com.google.common.collect.LinkedHashMultimap
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.management.FlinkDeploymentManager
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeConfig}
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion
import shapeless.syntax.typeable.typeableOps

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.Try

object MockDeploymentManager {
  val savepointPath = "savepoints/123-savepoint"
  val stopSavepointPath = "savepoints/246-stop-savepoint"
  val maxParallelism = 10
}

class MockDeploymentManager(val defaultProcessStateStatus: StateStatus) extends FlinkDeploymentManager(ModelData(ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)), shouldVerifyBeforeDeploy = false, mainClassName = "UNUSED") {

  import MockDeploymentManager._

  def this() = {
    this(SimpleStateStatus.Running)
  }

  private def prepareProcessState(status: StateStatus): Option[ProcessState] =
    prepareProcessState(status, Some(ProcessVersion.empty))

  private def prepareProcessState(status: StateStatus, version: Option[ProcessVersion]): Option[ProcessState] =
    Some(SimpleProcessStateDefinitionManager.processState(status, Some(ExternalDeploymentId("1")), version))

  override def getFreshProcessState(name: ProcessName): Future[Option[ProcessState]] = {
    Future {
      Thread.sleep(delayBeforeStateReturn.toMillis)
      managerProcessState.getOrDefault(name, prepareProcessState(defaultProcessStateStatus))
    }
  }

  override protected def getFreshProcessState(name: ProcessName, lastAction: Option[ProcessAction]): Future[Option[ProcessState]] =
    getFreshProcessState(name)

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData,
                      canonicalProcess: CanonicalProcess, savepoint: Option[String]): Future[Option[ExternalDeploymentId]] = {
    logger.debug(s"Adding deploy for ${processVersion.processName}")
    deploys.add(processVersion.processName)
    synchronized {
      Option(deployResult.get(processVersion.processName)).map(_.toArray(Array.empty[Future[Option[ExternalDeploymentId]]]))
        .getOrElse(Array.empty)
        .lastOption
        .getOrElse(Future.successful(None))
    }
  }

  override protected def waitForDuringDeployFinished(processName: ProcessName): Future[Unit] = Future.successful(())

  private val deployResult = LinkedHashMultimap.create[ProcessName, Future[Option[ExternalDeploymentId]]]

  private var cancelResult: Future[Unit] = Future.successful(())

  private val managerProcessState = new ConcurrentHashMap[ProcessName, Option[ProcessState]]

  @volatile
  private var delayBeforeStateReturn: FiniteDuration = 0 seconds

  //queue of invocations to e.g. check that deploy was already invoked in "ProcessManager"
  val deploys = new ConcurrentLinkedQueue[ProcessName]()

  def withWaitForDeployFinish[T](name: ProcessName)(action: => T): T = {
    val promise = Promise[Option[ExternalDeploymentId]]()
    val future = promise.future
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

  def withProcessFinished[T](processName: ProcessName)(action: => T): T = {
    withProcessStateStatus(processName, SimpleStateStatus.Finished)(action)
  }

  def withProcessStateStatus[T](processName: ProcessName, status: StateStatus)(action: => T): T = {
    withProcessState(processName, prepareProcessState(status))(action)
  }

  def withProcessStateVersion[T](processName: ProcessName, status: StateStatus, version: Option[ProcessVersion])(action: => T): T = {
    withProcessState(processName, prepareProcessState(status, version))(action)
  }

  def withEmptyProcessState[T](processName: ProcessName)(action: => T): T = {
    withProcessState(processName, None)(action)
  }

  def withProcessState[T](processName: ProcessName, status: Option[ProcessState])(action: => T): T = {
    try {
      managerProcessState.put(processName, status)
      action
    } finally {
      managerProcessState.remove(processName)
    }
  }

  override protected def makeSavepoint(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult] = Future.successful(SavepointResult(path = savepointPath))

  override protected def stop(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult] = Future.successful(SavepointResult(path = stopSavepointPath))

  override protected def runProgram(processName: ProcessName, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = ???

  override def customActions: List[CustomAction] = {
    import SimpleStateStatus._
    List(
      CustomAction(name = "hello", allowedStateStatusNames = List(ProblemStateStatus.name, NotDeployed.name)),
      CustomAction(name = "not-implemented", allowedStateStatusNames = List(ProblemStateStatus.name, NotDeployed.name)),
      CustomAction(name = "invalid-status", allowedStateStatusNames = Nil)
    )
  }

  override def invokeCustomAction(actionRequest: CustomActionRequest, canonicalProcess: CanonicalProcess): Future[Either[CustomActionError, CustomActionResult]] =
    Future.successful {
      actionRequest.name match {
        case "hello" | "invalid-status" => Right(CustomActionResult(actionRequest, "Hi"))
        case _ => Left(CustomActionNotImplemented(actionRequest))
      }
    }

  override def close(): Unit = {}

  override def cancel(name: ProcessName, user: User): Future[Unit] = cancelResult

  override protected def cancel(deploymentId: ExternalDeploymentId): Future[Unit] = Future.successful(())

  override protected def checkRequiredSlotsExceedAvailableSlots(canonicalProcess: CanonicalProcess, currentlyDeployedJobId: Option[ExternalDeploymentId]): Future[Unit] =
    if (canonicalProcess.metaData.typeSpecificData.cast[StreamMetaData].flatMap(_.parallelism).exists(_ > maxParallelism)) {
      Future.failed(new IllegalArgumentException("Parallelism too large"))
    } else {
      Future.successful(())
    }


}
