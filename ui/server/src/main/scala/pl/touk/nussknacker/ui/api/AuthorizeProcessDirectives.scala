package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directive0, Directives}
import pl.touk.nussknacker.restmodel.process.{ProcessId, ProcessIdWithName}
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

import scala.language.implicitConversions

trait AuthorizeProcessDirectives {
  val processAuthorizer: AuthorizeProcess

  protected implicit def attachImplicitUserToProcessId(id: ProcessId)(implicit loggedUser: LoggedUser): (ProcessId, LoggedUser) = (id, loggedUser)

  protected implicit def attachImplicitUserToProcessIdWithName(id: ProcessIdWithName)(implicit loggedUser: LoggedUser): (ProcessId, LoggedUser) = (id.id, loggedUser)

  def canDeploy(processIdAndUser: (ProcessId, LoggedUser)): Directive0 = {
    hasUserPermissionInProcess(processIdAndUser, Permission.Deploy)
  }

  def canWrite(processIdAndUser: (ProcessId, LoggedUser)): Directive0 = {
    hasUserPermissionInProcess(processIdAndUser, Permission.Write)
  }

  private def canInProcess(processId: ProcessId, permission: Permission, user:LoggedUser) :Directive0 = {
    Directives.authorizeAsync(_ => processAuthorizer.check(processId, permission,user))
  }

  def hasAdminPermission(loggedUser: LoggedUser): Directive0 = {
    Directives.authorize(loggedUser.isAdmin)
  }

  private def hasUserPermissionInProcess(processIdAndUser: (ProcessId, LoggedUser), permission: Permission.Value): Directive0 = {
    val (processId, user) = processIdAndUser
    canInProcess(processId, permission, user)
  }
}
