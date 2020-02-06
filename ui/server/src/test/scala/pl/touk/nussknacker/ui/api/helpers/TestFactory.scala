package pl.touk.nussknacker.ui.api.helpers

import java.util.concurrent.atomic.{AtomicReference}

import akka.http.scaladsl.server.Route
import cats.instances.future._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, ProcessDeploymentData, ProcessState, SavepointResult, StateStatus, User}
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessState, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.{FlinkProcessManager, FlinkStreamingProcessManagerProvider}
import pl.touk.nussknacker.ui.api.{RouteWithUser, RouteWithoutUser}
import pl.touk.nussknacker.ui.api.helpers.TestPermissions.CategorizedPermission
import pl.touk.nussknacker.ui.db.DbConfig
import pl.touk.nussknacker.ui.process.repository.{DBFetchingProcessRepository, _}
import pl.touk.nussknacker.ui.process.subprocess.{DbSubprocessRepository, SubprocessDetails, SubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.validation.ProcessValidation

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

//TODO: merge with ProcessTestData?
object TestFactory extends TestPermissions{

  val testCategoryName: String = TestPermissions.testCategoryName
  val secondTestCategoryName: String = TestPermissions.secondTestCategoryName

  //FIIXME: remove testCategory dommy implementation
  val testCategory:CategorizedPermission= Map(
    testCategoryName -> Permission.ALL_PERMISSIONS,
    secondTestCategoryName -> Permission.ALL_PERMISSIONS
  )

  val sampleSubprocessRepository = new SampleSubprocessRepository(Set(ProcessTestData.sampleSubprocess))
  val sampleResolver = new SubprocessResolver(sampleSubprocessRepository)

  val processValidation = new ProcessValidation(
    Map(TestProcessingTypes.Streaming -> ProcessTestData.validator),
    Map(TestProcessingTypes.Streaming -> Map()),
    sampleResolver,
    Map.empty
  )
  val processResolving = new UIProcessResolving(processValidation, Map.empty)
  val posting = new ProcessPosting
  val buildInfo = Map("engine-version" -> "0.1")

  def newProcessRepository(dbs: DbConfig, modelVersions: Option[Int] = Some(1)) =
    new DBFetchingProcessRepository[Future](dbs) with BasicRepository

  def newWriteProcessRepository(dbs: DbConfig, modelVersions: Option[Int] = Some(1)) =
    new DbWriteProcessRepository[Future](dbs, modelVersions.map(TestProcessingTypes.Streaming -> _).toMap)
        with WriteProcessRepository with BasicRepository

  def newSubprocessRepository(db: DbConfig) = {
    new DbSubprocessRepository(db, implicitly[ExecutionContext])
  }

  def newDeploymentProcessRepository(db: DbConfig) = new ProcessActionRepository(db,
    Map(TestProcessingTypes.Streaming -> buildInfo))

  def newProcessActivityRepository(db: DbConfig) = new ProcessActivityRepository(db)

  def asAdmin(route: RouteWithUser): Route = {
    route.securedRoute(adminUser())
  }

  def withPermissions(route: RouteWithUser, permissions: TestPermissions.CategorizedPermission): Route =
    route.securedRoute(user(permissions = permissions))

  //FIXME: update
  def withAllPermissions(route: RouteWithUser): Route = withPermissions(route, testPermissionAll)

  def withAdminPermissions(route: RouteWithUser): Route = route.securedRoute(adminUser())

  def withoutPermissions(route: RouteWithoutUser): Route = route.publicRoute()


  //FIXME: update
  def user(id: String = "1", username: String = "user", permissions: CategorizedPermission = testPermissionEmpty): LoggedUser = LoggedUser(id, username, permissions)

  def adminUser(id: String = "1", username: String = "admin"): LoggedUser = LoggedUser(id, username, Map.empty, Nil, isAdmin = true)

  object MockProcessManager {
    val savepointPath = "savepoints/123-savepoint"
    val stopSavepointPath = "savepoints/246-stop-savepoint"
  }

  class MockProcessManager extends FlinkProcessManager(FlinkStreamingProcessManagerProvider.defaultModelData(ConfigWithScalaVersion.config), shouldVerifyBeforeDeploy = false, mainClassName = "UNUSED"){

    import MockProcessManager._

    private def prepareProcessState(status: StateStatus): Option[ProcessState ]=
      prepareProcessState(status, Some(ProcessVersion.empty))

    private def prepareProcessState(status: StateStatus, version: Option[ProcessVersion]): Option[ProcessState] =
      Some(SimpleProcessState(DeploymentId("1"), status, version))

    override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] =
      Future.successful(managerProcessState.get())

    override def deploy(processId: ProcessVersion, processDeploymentData: ProcessDeploymentData, savepoint: Option[String], user: User): Future[Unit] =
      deployResult

    private var deployResult: Future[Unit] = Future.successful()

    private val managerProcessState = new AtomicReference[Option[ProcessState]](prepareProcessState(SimpleStateStatus.Running))

    def withWaitForDeployFinish[T](action: => T): T = {
      val promise = Promise[Unit]
      try {
        deployResult = promise.future
        action
      } finally {
        promise.complete(Try(Unit))
        deployResult = Future.successful()
      }
    }

    def withFailingDeployment[T](action: => T): T = {
      deployResult = Future.failed(new RuntimeException("Failing deployment..."))
      try {
        action
      } finally {
        deployResult = Future.successful()
      }
    }

    def withProcessFinished[T](action: => T): T = {
      try {
        managerProcessState.set(prepareProcessState(SimpleStateStatus.Finished))
        action
      } finally {
        managerProcessState.set(prepareProcessState(SimpleStateStatus.Running))
      }
    }

    def withProcessStateStatus[T](status: StateStatus)(action: => T): T = {
      try {
        managerProcessState.set(prepareProcessState(status))
        action
      } finally {
        managerProcessState.set(prepareProcessState(SimpleStateStatus.Running))
      }
    }

    def withProcessStateVersion[T](status: StateStatus, version: Option[ProcessVersion])(action: => T): T = {
      try {
        managerProcessState.set(prepareProcessState(status, version))
        action
      } finally {
        managerProcessState.set(prepareProcessState(SimpleStateStatus.Running))
      }
    }

    def withNotFoundProcessState[T](action: => T): T = {
      try {
        managerProcessState.set(Option.empty)
        action
      } finally {
        managerProcessState.set(prepareProcessState(SimpleStateStatus.Running))
      }
    }

    def withNotDeployedProcessState[T](action: => T): T = {
      try {
        managerProcessState.set(Option.empty)
        action
      } finally {
        managerProcessState.set(prepareProcessState(SimpleStateStatus.Running))
      }
    }


    override protected def cancel(job: ProcessState): Future[Unit] = Future.successful(Unit)

    override protected def makeSavepoint(job: ProcessState, savepointDir: Option[String]): Future[SavepointResult] = Future.successful(SavepointResult(path = savepointPath))

    override protected def stop(job: ProcessState, savepointDir: Option[String]): Future[SavepointResult] = Future.successful(SavepointResult(path = stopSavepointPath))

    override protected def runProgram(processName: ProcessName, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Unit] = ???
  }

  class SampleSubprocessRepository(subprocesses: Set[CanonicalProcess]) extends SubprocessRepository {
    override def loadSubprocesses(versions: Map[String, Long]): Set[SubprocessDetails] =
      subprocesses.map(c => SubprocessDetails(c, testCategoryName))
  }
}
