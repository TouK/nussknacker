package pl.touk.nussknacker.ui.api.helpers

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}

import akka.http.scaladsl.server.Route
import cats.instances.future._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StatusState.StateStatus
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, ProcessDeploymentData, ProcessState}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.customs.deployment.CustomStateStatus
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
import scala.concurrent.{ExecutionContext, Future}

//TODO: merge with ProcessTestData?
object TestFactory extends TestPermissions{

  val testCategoryName: String = TestPermissions.testCategoryName
  val secondTestCategoryName: String = TestPermissions.secondTestCategoryName

  //FIIXME: remove testCategory dommy implementation
  val testCategory:CategorizedPermission= Map(
    testCategoryName -> Permission.ALL_PERMISSIONS,
    secondTestCategoryName -> Permission.ALL_PERMISSIONS
  )

  val testEnvironment = "test"

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

  def newDeploymentProcessRepository(db: DbConfig) = new DeployedProcessRepository(db,
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
  def user(id: String = "1", username: String = "user", permissions: CategorizedPermission = testPermissionEmpty) = LoggedUser(id, username, permissions)

  def adminUser(id: String = "1", username: String = "admin") = LoggedUser(id, username, Map.empty, Nil, isAdmin = true)

  class MockProcessManager extends FlinkProcessManager(FlinkStreamingProcessManagerProvider.defaultModelData(ConfigWithScalaVersion.config), shouldVerifyBeforeDeploy = false, mainClassName = "UNUSED"){

    override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = Future.successful(
      Some(ProcessState(DeploymentId("1"), managerProcessState.get(), None))
    )

    import ExecutionContext.Implicits.global

    override def deploy(processId: ProcessVersion, processDeploymentData: ProcessDeploymentData, savepoint: Option[String]): Future[Unit] = Future {
      Thread.sleep(sleepBeforeAnswer.get())
      if (failDeployment.get()) {
        throw new RuntimeException("Failing deployment...")
      } else {
        ()
      }
    }

    private val sleepBeforeAnswer = new AtomicLong(0)
    private val failDeployment = new AtomicBoolean(false)
    private val managerProcessState = new AtomicReference[StateStatus](CustomStateStatus.Running)

    def withLongerSleepBeforeAnswer[T](action: => T): T = {
      try {
        sleepBeforeAnswer.set(500)
        action
      } finally {
        sleepBeforeAnswer.set(0)
      }
    }

    def withFailingDeployment[T](action: => T): T = {
      try {
        failDeployment.set(true)
        action
      } finally {
        failDeployment.set(false)
      }
    }

    def withProcessFinished[T](action: => T): T = {
      try {
        managerProcessState.set(CustomStateStatus.Finished)
        action
      } finally {
        managerProcessState.set(CustomStateStatus.Running)
      }
    }

    override protected def cancel(job: ProcessState): Future[Unit] = Future.successful(Unit)

    override protected def makeSavepoint(job: ProcessState, savepointDir: Option[String]): Future[String] = Future.successful("dummy")

    override protected def runProgram(processName: ProcessName, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Unit] = ???
  }

  class SampleSubprocessRepository(subprocesses: Set[CanonicalProcess]) extends SubprocessRepository {
    override def loadSubprocesses(versions: Map[String, Long]): Set[SubprocessDetails] =
      subprocesses.map(c => SubprocessDetails(c, testCategoryName))
  }
}
