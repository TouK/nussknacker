package pl.touk.esp.ui.api

import akka.http.scaladsl.server.Route
import pl.touk.esp.ui.security.LoggedUser

trait RouteWithUser {

  def route(implicit user: LoggedUser): Route

}
