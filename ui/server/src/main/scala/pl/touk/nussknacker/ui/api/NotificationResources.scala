package pl.touk.nussknacker.ui.api

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.Materializer
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.ui.notifications.NotificationService
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class NotificationResources(notificationsService: NotificationService)
                           (implicit ec: ExecutionContext, mat: Materializer, system: ActorSystem)
  extends Directives
    with LazyLogging
    with RouteWithUser with FailFastCirceSupport {

  //TODO: in the future we could use https://github.com/akka/akka-http/pull/1828 when we can bump version to 10.1.x
  private val durationFromConfig = system.settings.config.getDuration("akka.http.server.request-timeout")
  private implicit val timeout: Timeout = Timeout(durationFromConfig.toMillis millis)

  def securedRoute(implicit user: LoggedUser): Route = {
    path("notifications") {
      parameter('after.as[Instant].optional) { notificationsAfter =>
        get {
          complete {
            notificationsService.notifications(user, notificationsAfter)
          }
        }
      }
    }
  }

}

