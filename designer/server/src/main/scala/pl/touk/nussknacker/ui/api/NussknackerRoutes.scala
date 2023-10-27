package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import pl.touk.nussknacker.ui.security.api.LoggedUser

trait RouteWithUser {

  final def securedRouteWithErrorHandling(implicit user: LoggedUser): Route = {
    handleExceptions(NuDesignerErrorToHttp.nuDesignerErrorHandler) {
      securedRoute
    }
  }

  protected def securedRoute(implicit user: LoggedUser): Route
}

trait RouteWithoutUser {

  final def publicRouteWithErrorHandling(): Route = {
    handleExceptions(NuDesignerErrorToHttp.nuDesignerErrorHandler) {
      publicRoute()
    }
  }

  protected def publicRoute(): Route
}
