package pl.touk.nussknacker.ui.security

import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.server.Directive
import akka.http.scaladsl.server.directives.{MethodDirectives, SecurityDirectives}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

trait HttpMethodBasedAuthorize extends MethodDirectives with SecurityDirectives {

  def authorizeMethod(persmissionsOnPostPutDelete: Permission.Value, user: LoggedUser) = extractMethod.flatMap[Unit] {
    case HttpMethods.POST | HttpMethods.PUT | HttpMethods.DELETE => authorize(user.hasPermission(persmissionsOnPostPutDelete))
    case HttpMethods.GET => authorize(user.hasPermission(Permission.Read))
    case _ => Directive.Empty
  }


}
