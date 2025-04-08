package pl.touk.nussknacker.ui.customhttpservice

import cats.effect.IO
import org.apache.pekko.http.scaladsl.server.Route
import pl.touk.nussknacker.ui.customhttpservice.TapirCustomHttpServiceProvider.CustomHttpServiceServerEndpointDefinition
import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.Endpoint

sealed trait CustomHttpServiceProvider

trait PekkoCustomHttpServiceProvider extends CustomHttpServiceProvider {
  def provideRouteWithUser(implicit user: LoggedUser): Route
}

trait TapirCustomHttpServiceProvider extends CustomHttpServiceProvider {
  def serverEndpointDefinitions: List[CustomHttpServiceServerEndpointDefinition]
}

object TapirCustomHttpServiceProvider {

  trait CustomHttpServiceServerEndpointDefinition {

    type REQUEST
    type ERROR
    type RESPONSE

    def definition: Endpoint[Unit, REQUEST, ERROR, RESPONSE, Any with PekkoStreams]

    def logic(user: LoggedUser, request: REQUEST): IO[Either[ERROR, RESPONSE]]

  }

}
