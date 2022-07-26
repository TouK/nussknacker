package pl.touk.nussknacker.ui.notifications

import akka.util.Timeout
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.ListenerApiUser
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.OnDeployActionFailed
import pl.touk.nussknacker.ui.process.deployment.DeploymentActionType.Deployment
import pl.touk.nussknacker.ui.process.deployment.{DeployInfo, DeploymentStatusResponse}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class NotificationServiceTest extends FunSuite with Matchers with PatientScalaFutures {

  private implicit val timeout: Timeout = Timeout(1 second)

  test("Should return only events for user in given time") {
    var currentInstant: Instant = Instant.now()

    val clock: Clock = clockForInstant(() => currentInstant)

    val currentDeployments = new CurrentDeployments {
      override def retrieve(implicit timeout: Timeout): Future[DeploymentStatusResponse] = Future.successful(DeploymentStatusResponse(
        Map(ProcessName("id1") -> DeployInfo("deployingUser", clock.millis(), Deployment))))
    }
    val listener = new NotificationsListener(NotificationConfig(20 minutes), (id: ProcessId) => Future.successful(Some(ProcessName(id.value + "-name"))), clock)
    val notificationService = new NotificationService(currentDeployments, listener)
    def notificationsFor(user: String, after: Option[Instant] = None) = notificationService.notifications(LoggedUser(user, ""), after).futureValue


    val refreshAll = List(DataToRefresh.versions, DataToRefresh.activity)

    notificationsFor("deployingUser") shouldBe 'empty
    notificationsFor("randomUser").map(_.toRefresh) shouldBe List(Nil)

    val userId = "user1"
    listener.handle(OnDeployActionFailed(ProcessId(1), new RuntimeException("Failure")))(global, ListenerApiUser(LoggedUser(userId, "")))
    notificationsFor(userId).map(_.toRefresh) shouldBe List(Nil, refreshAll)
    notificationsFor("user2").map(_.toRefresh) shouldBe List(Nil)

    notificationsFor(userId, Some(currentInstant.minusSeconds(20))).map(_.toRefresh) shouldBe  List(Nil, refreshAll)
    notificationsFor(userId, Some(currentInstant.plusSeconds(20))).map(_.toRefresh) shouldBe List(Nil)

    currentInstant = currentInstant.plus(1, ChronoUnit.HOURS)
    notificationsFor(userId).map(_.toRefresh) shouldBe List(Nil)
  }

  private def clockForInstant(currentInstant: () => Instant): Clock = {
    new Clock {
      override def getZone: ZoneId = ZoneId.systemDefault()

      override def withZone(zone: ZoneId): Clock = ???

      override def instant(): Instant = currentInstant()
    }
  }
}
