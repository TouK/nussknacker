package pl.touk.esp.ui.api.helpers

import java.io.File

import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import db.migration.DefaultJdbcProfile
import pl.touk.esp.engine.api.deployment.test.{TestData, TestResults}
import pl.touk.esp.engine.api.deployment.{ProcessDeploymentData, ProcessManager, ProcessState}
import pl.touk.esp.engine.management.FlinkProcessManager
import pl.touk.esp.ui.api.{ProcessPosting, ProcessTestData, ProcessValidation}
import pl.touk.esp.ui.app.BuildInfoHolder
import pl.touk.esp.ui.process.marshall.ProcessConverter
import pl.touk.esp.ui.process.repository.{DeployedProcessRepository, ProcessActivityRepository, ProcessRepository}
import pl.touk.esp.ui.security.{LoggedUser, Permission}
import pl.touk.esp.ui.security.Permission.Permission
import slick.jdbc.JdbcBackend

import scala.concurrent.Future

object TestFactory {

  val testCategory = "TESTCAT"

  val processValidation = new ProcessValidation(ProcessTestData.validator)
  val processConverter = new ProcessConverter(processValidation)
  val posting = new ProcessPosting(processConverter)

  def newProcessRepository(db: JdbcBackend.Database) = new ProcessRepository(db, DefaultJdbcProfile.profile, processConverter)

  val buildInfoHolder = new BuildInfoHolder(Map("engine-version" -> "0.1"))

  def newDeploymentProcessRepository(db: JdbcBackend.Database) = new DeployedProcessRepository(db, DefaultJdbcProfile.profile,
    buildInfoHolder)
  def newCommentsRepository(db: JdbcBackend.Database) = new ProcessActivityRepository(db, DefaultJdbcProfile.profile)
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
      override def deploy(processId: String, processDeploymentData: ProcessDeploymentData): Future[Unit] = Future {
        Thread.sleep(sleepBeforeAnswer)
        println("FINISHED?")
        ()
      }
    }
  }

  def user(permissions: Permission*) = LoggedUser("userId", "pass", permissions.toList, List(testCategory))

  val allPermissions = List(Permission.Deploy, Permission.Read, Permission.Write)
  def withPermissions(route: LoggedUser => Route, permissions: Permission*) = route(user(permissions : _*))
  def withAllPermissions(route: LoggedUser => Route) = route(user(allPermissions: _*))

}
