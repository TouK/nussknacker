package pl.touk.nussknacker.ui.notifications

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext._
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.ListenerApiUser
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.{OnDeployActionFailed, OnDeployActionSuccess}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class NotificationServiceTest extends AnyFunSuite with Matchers with PatientScalaFutures {

  test("Should return only events for user in given time") {
    var currentInstant: Instant = Instant.now()

    val clock: Clock = clockForInstant(() => currentInstant)

    val listener = new NotificationsListener(NotificationConfig(20 minutes), (id: ProcessId) => Future.successful(Some(ProcessName("" + id.value + "-name"))), clock)
    val notificationService = new NotificationService(listener)
    def notificationsFor(user: String, after: Option[Instant] = None): List[Notification] = notificationService.notifications(LoggedUser(user, user), after)


    val refreshAfterSuccess = List(DataToRefresh.versions, DataToRefresh.activity)
    val refreshAfterFail = List(DataToRefresh.state)

    notificationsFor("deployingUser") shouldBe Symbol("empty")
    notificationsFor("randomUser").map(_.toRefresh) shouldBe Symbol("empty")

    val userIdForFail = "user1"
    val userIdForSuccess = "user2"

    listener.handle(OnDeployActionSuccess(ProcessId(1), VersionId(1), None, Instant.now(), ProcessActionType.Cancel))(ctx, ListenerApiUser(LoggedUser(userIdForSuccess, "")))
    notificationsFor(userIdForSuccess).map(_.toRefresh) shouldBe List(refreshAfterSuccess)

    listener.handle(OnDeployActionFailed(ProcessId(1), new RuntimeException("Failure")))(ctx, ListenerApiUser(LoggedUser(userIdForFail, "")))
    notificationsFor(userIdForFail).map(_.toRefresh) shouldBe List(refreshAfterFail)

    notificationsFor(userIdForFail, Some(currentInstant.minusSeconds(20))).map(_.toRefresh) shouldBe  List(refreshAfterFail)
    notificationsFor(userIdForFail, Some(currentInstant.plusSeconds(20))).map(_.toRefresh) shouldBe Symbol("empty")

    currentInstant = currentInstant.plus(1, ChronoUnit.HOURS)
    notificationsFor(userIdForFail).map(_.toRefresh) shouldBe Symbol("empty")
  }

  private def clockForInstant(currentInstant: () => Instant): Clock = {
    new Clock {
      override def getZone: ZoneId = ZoneId.systemDefault()

      override def withZone(zone: ZoneId): Clock = ???

      override def instant(): Instant = currentInstant()
    }
  }
}
