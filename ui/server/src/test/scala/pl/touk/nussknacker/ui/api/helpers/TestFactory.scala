package pl.touk.nussknacker.ui.api.helpers

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{ProcessDeploymentData, ProcessState}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.{FlinkModelData, FlinkProcessManager}
import pl.touk.nussknacker.ui.api.helpers.TestPermissions.{CategorizedPermission, testCategoryName}
import pl.touk.nussknacker.ui.api.{ProcessPosting, ProcessTestData, RouteWithUser}
import pl.touk.nussknacker.ui.db.DbConfig
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.process.repository.{DBFetchingProcessRepository, FetchingProcessRepository, _}
import pl.touk.nussknacker.ui.process.subprocess.{DbSubprocessRepository, SubprocessDetails, SubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.validation.ProcessValidation
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import slick.jdbc.JdbcBackend

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

  val processValidation = new ProcessValidation(Map(ProcessingType.Streaming -> ProcessTestData.validator), sampleResolver)
  val posting = new ProcessPosting
  val buildInfo = Map("engine-version" -> "0.1")

  def newProcessRepository(dbs: DbConfig, modelVersions: Option[Int] = Some(1)) =
    new DBFetchingProcessRepository[Future](dbs) with FetchingProcessRepository with BasicRepository

  def newWriteProcessRepository(dbs: DbConfig, modelVersions: Option[Int] = Some(1)) =
    new DbWriteProcessRepository[Future](dbs, modelVersions.map(ProcessingType.Streaming -> _).toMap)
        with WriteProcessRepository with BasicRepository

  def newSubprocessRepository(db: DbConfig) = {
    new DbSubprocessRepository(db, implicitly[ExecutionContext])
  }

  def newDeploymentProcessRepository(db: DbConfig) = new DeployedProcessRepository(db,
    Map(ProcessingType.Streaming -> buildInfo))

  def newProcessActivityRepository(db: DbConfig) = new ProcessActivityRepository(db)

  def withPermissions(route: RouteWithUser, permissions: TestPermissions.CategorizedPermission) =
    route.route(user(permissions))

  //FIXME: update
  def user(testPermissions: CategorizedPermission = testPermissionEmpty) = LoggedUser("userId", testPermissions)

  //FIXME: update
  def withAllPermissions(route: RouteWithUser) = withPermissions(route, testPermissionAll)

  class MockProcessManager extends FlinkProcessManager(FlinkModelData(ConfigFactory.load()), false, null){

    override def findJobStatus(name: String): Future[Option[ProcessState]] = Future.successful(None)

    override def cancel(name: String): Future[Unit] = Future.successful(Unit)

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
  }

  object SampleSubprocessRepository extends SubprocessRepository {
    val subprocesses = Set(ProcessTestData.sampleSubprocess)

    override def loadSubprocesses(versions: Map[String, Long]): Set[SubprocessDetails] = {
      subprocesses.map(c => SubprocessDetails(c, testCategoryName))
    }
  }

}
