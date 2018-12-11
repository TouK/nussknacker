package pl.touk.nussknacker.ui.api.helpers

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, ProcessDeploymentData, ProcessState}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.{FlinkProcessManager, FlinkProcessManagerProvider}
import pl.touk.nussknacker.ui.api.helpers.TestPermissions.CategorizedPermission
import pl.touk.nussknacker.ui.api.{ProcessPosting, ProcessTestData, RouteWithUser}
import pl.touk.nussknacker.ui.db.DbConfig
import pl.touk.nussknacker.ui.process.repository.{DBFetchingProcessRepository, FetchingProcessRepository, _}
import pl.touk.nussknacker.ui.process.subprocess.{DbSubprocessRepository, SubprocessDetails, SubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.validation.ProcessValidation
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

//TODO: merge with ProcessTestData?
object TestFactory extends TestPermissions{

  val testCategoryName: String = TestPermissions.testCategoryName

  //FIIXME: remove testCategory dommy implementation
  val testCategory:CategorizedPermission= Map(testCategoryName -> Set.empty)
  val testEnvironment = "test"

  val sampleSubprocessRepository = SampleSubprocessRepository
  val sampleResolver = new SubprocessResolver(sampleSubprocessRepository)

  val processValidation = new ProcessValidation(Map(TestProcessingTypes.Streaming -> ProcessTestData.validator),
    Map(TestProcessingTypes.Streaming -> Map()), sampleResolver)
  val posting = new ProcessPosting
  val buildInfo = Map("engine-version" -> "0.1")

  def newProcessRepository(dbs: DbConfig, modelVersions: Option[Int] = Some(1)) =
    new DBFetchingProcessRepository[Future](dbs) with FetchingProcessRepository with BasicRepository

  def newWriteProcessRepository(dbs: DbConfig, modelVersions: Option[Int] = Some(1)) =
    new DbWriteProcessRepository[Future](dbs, modelVersions.map(TestProcessingTypes.Streaming -> _).toMap)
        with WriteProcessRepository with BasicRepository

  def newSubprocessRepository(db: DbConfig) = {
    new DbSubprocessRepository(db, implicitly[ExecutionContext])
  }

  def newDeploymentProcessRepository(db: DbConfig) = new DeployedProcessRepository(db,
    Map(TestProcessingTypes.Streaming -> buildInfo))

  def newProcessActivityRepository(db: DbConfig) = new ProcessActivityRepository(db)

  def withPermissions(route: RouteWithUser, permissions: TestPermissions.CategorizedPermission) =
    route.route(user(permissions))

  //FIXME: update
  def user(testPermissions: CategorizedPermission = testPermissionEmpty) = LoggedUser("userId", testPermissions)

  //FIXME: update
  def withAllPermissions(route: RouteWithUser) = withPermissions(route, testPermissionAll)

  class MockProcessManager extends FlinkProcessManager(FlinkProcessManagerProvider.defaultModelData(ConfigFactory.load()), false){

    override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = Future.successful(Some(ProcessState(DeploymentId("1"), "RUNNING", 0)))

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

    override protected def cancel(job: ProcessState): Future[Unit] = Future.successful(Unit)

    override protected def makeSavepoint(job: ProcessState, savepointDir: Option[String]): Future[String] = Future.successful("dummy")

    override protected def runProgram(processName: ProcessName, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Unit] = ???
  }

  object SampleSubprocessRepository extends SubprocessRepository {
    val subprocesses = Set(ProcessTestData.sampleSubprocess)

    override def loadSubprocesses(versions: Map[String, Long]): Set[SubprocessDetails] = {
      subprocesses.map(c => SubprocessDetails(c, testCategoryName))
    }
  }

}
