package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.server.directives.{AuthenticationDirective, RouteDirectives}
import com.typesafe.config.Config
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

trait AuthenticationResources {
  val directive: AuthenticationDirective[LoggedUser]
  val route: Route = Directives.reject
  val config: AuthenticationConfiguration
}

object AuthenticationResources {
  type LoggedUserAuth = AuthenticationDirective[LoggedUser]

  def apply(directive: AuthenticationDirective[LoggedUser], config: AuthenticationConfiguration, route: Route = Directives.reject): AuthenticationResources =
    ((params: (AuthenticationDirective[LoggedUser], AuthenticationConfiguration, Route)) => new AuthenticationResources {
      override val directive: AuthenticationDirective[LoggedUser] = params._1
      override val config: AuthenticationConfiguration = params._2
      override val route: Route = params._3
    })((directive, config, route))

  def apply(config: Config, classLoader: ClassLoader, allCategories: List[String])(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): AuthenticationResources = {
    AuthenticationProvider(config, classLoader, allCategories).createAuthenticationResources(config, classLoader, allCategories)
  }
}