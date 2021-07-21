package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.directives.AuthenticationDirective
import akka.http.scaladsl.server.{Directive1, Directives, Route}
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

trait AuthenticationResources extends Directives with FailFastCirceSupport {
  val name: String
  val frontendStrategySettings: FrontendStrategySettings

  def authenticate(): Directive1[AuthenticatedUser]

  final lazy val routeWithPathPrefix: Route =
    pathPrefix("authentication" / name.toLowerCase() ) {
      additionalRoute ~ frontendSettingsRoute
    }

  protected lazy val frontendSettingsRoute: Route = path("settings") { get { complete { ToResponseMarshallable(frontendStrategySettings) } } }
  protected lazy val additionalRoute: Route = Directives.reject
}

trait AnonymousAccess {
  val anonymousUserRole: Option[String]

  def authenticateReally(): AuthenticationDirective[AuthenticatedUser]

  def authenticate(): Directive1[AuthenticatedUser] = {
    anonymousUserRole.map(role => AuthenticatedUser("anonymous", "anonymous", List(role))).map(authenticateReally().withAnonymousUser(_)).getOrElse(authenticateReally())
  }
}

object AuthenticationResources {
  def apply(config: Config, classLoader: ClassLoader)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): AuthenticationResources = {
    AuthenticationProvider(config, classLoader).createAuthenticationResources(config, classLoader)
  }
}