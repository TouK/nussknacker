package pl.touk.nussknacker.ui.api.helpers

import java.io.File

import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import db.migration.DefaultJdbcProfile
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.deployment.test.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.deployment.{ProcessDeploymentData, ProcessManager, ProcessState}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{FlatNode, SplitNode}
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.{Split, SubprocessInputDefinition, SubprocessOutputDefinition}
import pl.touk.nussknacker.engine.management.FlinkProcessManager
import pl.touk.nussknacker.ui.validation.ProcessValidation
import pl.touk.nussknacker.ui.api.{ProcessPosting, ProcessTestData, RouteWithUser}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.{DeployedProcessRepository, ProcessActivityRepository, ProcessRepository}
import pl.touk.nussknacker.ui.process.subprocess.{SubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.security.{LoggedUser, Permission}
import pl.touk.nussknacker.ui.security.Permission.Permission
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}

object TestFactory {

  val testCategory = "TESTCAT"
  val testEnvironment = "test"

  val sampleSubprocessRepository = SampleSubprocessRepository
  val sampleResolver = new SubprocessResolver(sampleSubprocessRepository)

  val processValidation = new ProcessValidation(Map(ProcessingType.Streaming -> ProcessTestData.validator), sampleResolver)
  val posting = new ProcessPosting

  def newProcessRepository(db: JdbcBackend.Database) = new ProcessRepository(db, DefaultJdbcProfile.profile, processValidation,
    ExecutionContext.Implicits.global)

  val buildInfo = Map("engine-version" -> "0.1")

  def newDeploymentProcessRepository(db: JdbcBackend.Database) = new DeployedProcessRepository(db, DefaultJdbcProfile.profile,
    buildInfo)
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

    val mockProcessManager = new FlinkProcessManager(ConfigFactory.load(), null) {
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
    override def loadSubprocesses(): Set[CanonicalProcess] = Set(
      CanonicalProcess(MetaData("sub1", StreamMetaData(), true), ExceptionHandlerRef(List()), List(
        FlatNode(SubprocessInputDefinition("in", List())),
        SplitNode(Split("split"), List(
          List(FlatNode(SubprocessOutputDefinition("out", "out1"))),
          List(FlatNode(SubprocessOutputDefinition("out2", "out2")))
        ))
      ))
    )
  }
}
