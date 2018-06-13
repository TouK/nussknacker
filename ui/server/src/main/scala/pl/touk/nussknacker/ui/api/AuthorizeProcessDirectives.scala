package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directive0, Directives}
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

import scala.language.implicitConversions

trait AuthorizeProcessDirectives {
  val processAuthorizer: AuthorizeProcess

  protected implicit def attachImplicitUserToString(string: String)(implicit loggedUser: LoggedUser): (String, LoggedUser) =
    (string, loggedUser)

  def canDeploy(processIdAndUser: (String, LoggedUser)): Directive0 = {
    hasUserPermissionInProcess(processIdAndUser, Permission.Deploy)
  }

  def canWrite(processIdAndUser: (String, LoggedUser)): Directive0 = {
    hasUserPermissionInProcess(processIdAndUser, Permission.Write)
  }
  private def canInProcess(processId:String, permission:Permission, user:LoggedUser):Directive0 = {
    Directives.authorizeAsync(_ => processAuthorizer.check(processId, permission,user))
  }
  private def hasUserPermissionInProcess(processIdAndUser: (String, LoggedUser), permission: Permission.Value): Directive0 = {
    val (processId, user) = processIdAndUser
    canInProcess(processId, permission, user)
  }

}
