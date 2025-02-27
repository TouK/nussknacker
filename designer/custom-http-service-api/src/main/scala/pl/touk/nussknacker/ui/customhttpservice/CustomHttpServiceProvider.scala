package pl.touk.nussknacker.ui.customhttpservice

import akka.http.scaladsl.server.Route
import pl.touk.nussknacker.ui.security.api.LoggedUser

trait CustomHttpServiceProvider {
  def provideRouteWithUser(implicit user: LoggedUser): Route
}
