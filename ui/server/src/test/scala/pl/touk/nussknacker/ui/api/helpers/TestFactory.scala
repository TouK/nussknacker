package pl.touk.nussknacker.ui.api.helpers

import db.migration.DefaultJdbcProfile
import pl.touk.nussknacker.engine.api.deployment.{ProcessDeploymentData, ProcessState}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.{FlinkModelData, FlinkProcessManager}
import pl.touk.nussknacker.ui.api.{ProcessPosting, ProcessTestData, RouteWithUser}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.process.repository.{DeployedProcessRepository, ProcessActivityRepository, ProcessRepository}
import pl.touk.nussknacker.ui.process.subprocess.DbSubprocessRepository
import pl.touk.nussknacker.ui.validation.ProcessValidation
import pl.touk.nussknacker.ui.process.subprocess.{SubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.security.api.Permission.Permission

import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global

//TODO: merge with ProcessTestData?
object TestFactory {

  val testCategory = "TESTCAT"
  val testEnvironment = "test"

  val sampleSubprocessRepository = SampleSubprocessRepository
  val sampleResolver = new SubprocessResolver(sampleSubprocessRepository)

  val processValidation = new ProcessValidation(Map(ProcessingType.Streaming -> ProcessTestData.validator), sampleResolver)
  val posting = new ProcessPosting

  def newProcessRepository(db: JdbcBackend.Database, modelVersion: Option[Int] = Some(1)) = new ProcessRepository(db, DefaultJdbcProfile.profile, processValidation,
    modelVersion.map(ProcessingType.Streaming -> _).toMap)

  def newSubprocessRepository(db: JdbcBackend.Database) = {
    new DbSubprocessRepository(db, DefaultJdbcProfile.profile, implicitly[ExecutionContext])
  }

  val buildInfo = Map("engine-version" -> "0.1")

  def newDeploymentProcessRepository(db: JdbcBackend.Database) = new DeployedProcessRepository(db, DefaultJdbcProfile.profile,
    Map(ProcessingType.Streaming -> buildInfo))
  def newProcessActivityRepository(db: JdbcBackend.Database) = new ProcessActivityRepository(db, DefaultJdbcProfile.profile)
  val mockProcessManager = InMemoryMocks.mockProcessManager

  object InMemoryMocks {

    private var sleepBeforeAnswer : Long = 0

    def withLongerSleepBeforeAnswer[T](action : => T) = {
      try {
        sleepBeforeAnswer = 500
        action
      } finally {
        sleepBeforeAnswer = 0
      }
    }

    val mockProcessManager = new FlinkProcessManager(FlinkModelData(), false, null) {
      override def findJobStatus(name: String): Future[Option[ProcessState]] = Future.successful(None)
      override def cancel(name: String): Future[Unit] = Future.successful(Unit)
      import ExecutionContext.Implicits.global
      override def deploy(processId: String, processDeploymentData: ProcessDeploymentData, savepoint: Option[String]): Future[Unit] = Future {
        Thread.sleep(sleepBeforeAnswer)
        ()
      }
    }
  }

  def user(permissions: Permission*) = LoggedUser("userId", "pass", permissions.toList, List(testCategory))

  val allPermissions = List(Permission.Deploy, Permission.Read, Permission.Write)
  def withPermissions(route: RouteWithUser, permissions: Permission*) = route.route(user(permissions : _*))
  def withAllPermissions(route: RouteWithUser) = route.route(user(allPermissions: _*))

  object SampleSubprocessRepository extends SubprocessRepository {
    val subprocesses = Set(ProcessTestData.sampleSubprocess)

    override def loadSubprocesses(versions: Map[String, Long]): Set[CanonicalProcess] = {
      subprocesses
    }
  }
}
