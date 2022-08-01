package pl.touk.nussknacker.ui.api.helpers

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

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.Try

object MockDeploymentManager {
  val savepointPath = "savepoints/123-savepoint"
  val stopSavepointPath = "savepoints/246-stop-savepoint"
  val maxParallelism = 10
}

class MockDeploymentManager(val defaultProcessStateStatus: StateStatus) extends FlinkDeploymentManager(ModelData(ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)), shouldVerifyBeforeDeploy = false, mainClassName = "UNUSED") {

  import MockDeploymentManager._

  def this() {
    this(SimpleStateStatus.Running)
  }

  private def prepareProcessState(status: StateStatus): Option[ProcessState] =
    prepareProcessState(status, Some(ProcessVersion.empty))

  private def prepareProcessState(status: StateStatus, version: Option[ProcessVersion]): Option[ProcessState] =
    Some(SimpleProcessStateDefinitionManager.processState(status, Some(ExternalDeploymentId("1")), version))

  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] =
    Future.successful(managerProcessState.get())

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess, savepoint: Option[String]): Future[Option[ExternalDeploymentId]] = {
    deploys.add(processVersion)
    deployResult
  }

  private var deployResult: Future[Option[ExternalDeploymentId]] = Future.successful(None)

  private val managerProcessState = new AtomicReference[Option[ProcessState]](prepareProcessState(defaultProcessStateStatus))

  //queue of invocations to e.g. check that deploy was already invoked in "ProcessManager"
  val deploys = new ConcurrentLinkedQueue[ProcessVersion]()

  def withWaitForDeployFinish[T](action: => T): T = {
    val promise = Promise[Option[ExternalDeploymentId]]
    try {
      deployResult = promise.future
      action
    } finally {
      promise.complete(Try(None))
      deployResult = Future.successful(None)
    }
  }

  def withFailingDeployment[T](action: => T): T = {
    deployResult = Future.failed(new RuntimeException("Failing deployment..."))
    try {
      action
    } finally {
      deployResult = Future.successful(None)
    }
  }

  def withProcessFinished[T](action: => T): T = {
    withProcessStateStatus(SimpleStateStatus.Finished)(action)
  }

  def withProcessStateStatus[T](status: StateStatus)(action: => T): T = {
    withProcessState(prepareProcessState(status))(action)
  }

  def withProcessStateVersion[T](status: StateStatus, version: Option[ProcessVersion])(action: => T): T = {
    withProcessState(prepareProcessState(status, version))(action)
  }

  def withEmptyProcessState[T](action: => T): T = {
    withProcessState(None)(action)
  }

  def withProcessState[T](status: Option[ProcessState])(action: => T): T = {
    try {
      managerProcessState.set(status)
      action
    } finally {
      managerProcessState.set(prepareProcessState(defaultProcessStateStatus))
    }
  }

  override protected def cancel(deploymentId: ExternalDeploymentId): Future[Unit] = Future.successful(Unit)

  override protected def makeSavepoint(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult] = Future.successful(SavepointResult(path = savepointPath))

  override protected def stop(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult] = Future.successful(SavepointResult(path = stopSavepointPath))

  override protected def runProgram(processName: ProcessName, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = ???

  override def customActions: List[CustomAction] = {
    import SimpleStateStatus._
    List(
      CustomAction(name = "hello", allowedStateStatusNames = List(Warning.name, NotDeployed.name)),
      CustomAction(name = "not-implemented", allowedStateStatusNames = List(Warning.name, NotDeployed.name)),
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

  override def cancel(name: ProcessName, user: User): Future[Unit] = Future.successful(Unit)

  override protected def checkRequiredSlotsExceedAvailableSlots(canonicalProcess: CanonicalProcess, currentlyDeployedJobId: Option[ExternalDeploymentId]): Future[Unit] =
    if (canonicalProcess.metaData.typeSpecificData.cast[StreamMetaData].flatMap(_.parallelism).exists(_ > maxParallelism)) {
      Future.failed(new IllegalArgumentException("Parallelism too large"))
    } else {
      Future.successful(())
    }
}
