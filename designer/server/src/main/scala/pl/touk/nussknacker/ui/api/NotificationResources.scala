package pl.touk.nussknacker.ui.api

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.ui.notifications.NotificationService
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.Instant
import scala.concurrent.ExecutionContext

class NotificationResources(notificationsService: NotificationService)
                           (implicit ec: ExecutionContext, system: ActorSystem)
  extends Directives
    with LazyLogging
    with RouteWithUser with FailFastCirceSupport {

  def securedRoute(implicit user: LoggedUser): Route = {
    path("notifications") {
      parameter(Symbol("after").as[Instant].optional) { notificationsAfter =>
        get {
          complete {
            notificationsService.notifications(notificationsAfter)
          }
        }
      }
    }
  }

}

