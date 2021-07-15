package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.directives.AuthenticationDirective
import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.config.Config
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

trait AuthenticationResources extends Directives {
  val name: String
  val frontendSettings: ToResponseMarshallable = StatusCodes.NoContent

  def authenticate(): AuthenticationDirective[AuthenticatedUser]

  final lazy val routeWithPathPrefix: Route =
    pathPrefix("authentication" / name.toLowerCase() ) {
      additionalRoute ~ frontendSettingsRoute
    }

  protected lazy val frontendSettingsRoute: Route = path("settings") { get { complete { frontendSettings } } }
  protected lazy val additionalRoute: Route = Directives.reject
}

object AuthenticationResources {
  def apply(config: Config, classLoader: ClassLoader)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): AuthenticationResources = {
    AuthenticationProvider(config, classLoader).createAuthenticationResources(config, classLoader)
  }
}