package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.Route
import pl.touk.nussknacker.ui.security.HttpMethodBasedAuthorize
import pl.touk.nussknacker.ui.security.api.LoggedUser

trait RouteWithUser extends HttpMethodBasedAuthorize {

  def route(implicit user: LoggedUser): Route

}
