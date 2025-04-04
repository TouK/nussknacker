package pl.touk.nussknacker.ui.customhttpservice

import org.apache.pekko.http.scaladsl.server.Route
import pl.touk.nussknacker.ui.customhttpservice.TapirCustomHttpServiceProvider.CustomHttpServiceServerEndpoint
import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future

sealed trait CustomHttpServiceProvider

trait PekkoCustomHttpServiceProvider extends CustomHttpServiceProvider {
  def provideRouteWithUser(implicit user: LoggedUser): Route
}

trait TapirCustomHttpServiceProvider extends CustomHttpServiceProvider {
  def serverEndpoints: List[CustomHttpServiceServerEndpoint]
}

object TapirCustomHttpServiceProvider {
  type CustomHttpServiceServerEndpoint = ServerEndpoint[PekkoStreams with WebSockets, Future]
}
