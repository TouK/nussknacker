package pl.touk.nussknacker.ui.customhttpservice

import akka.http.scaladsl.server.Directives.reject
import akka.http.scaladsl.server.Route
import pl.touk.nussknacker.ui.security.api.LoggedUser

trait CustomHttpServiceProvider {
  def provideRouteWithUser(implicit user: LoggedUser): Route
  def provideRouteWithoutUser(): Route
}

object CustomHttpServiceProvider {

  def noop: CustomHttpServiceProvider = new CustomHttpServiceProvider {
    override def provideRouteWithUser(implicit user: LoggedUser): Route = reject
    override def provideRouteWithoutUser(): Route                       = reject
  }

}
