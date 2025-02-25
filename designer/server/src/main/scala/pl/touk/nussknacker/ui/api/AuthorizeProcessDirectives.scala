package pl.touk.nussknacker.ui.api

import org.apache.pekko.http.scaladsl.server.{Directive0, Directives}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.RemoteUserName
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

trait AuthorizeProcessDirectives {
  val processAuthorizer: AuthorizeProcess

  protected implicit def attachImplicitUserToProcessId(id: ProcessId)(
      implicit loggedUser: LoggedUser
  ): (ProcessId, LoggedUser) = (id, loggedUser)

  protected implicit def attachImplicitUserToProcessIdWithName(id: ProcessIdWithName)(
      implicit loggedUser: LoggedUser
  ): (ProcessId, LoggedUser) = (id.id, loggedUser)

  def canDeploy(processIdAndUser: (ProcessId, LoggedUser)): Directive0 = {
    hasUserPermissionInProcess(processIdAndUser, Permission.Deploy)
  }

  def canWrite(processIdAndUser: (ProcessId, LoggedUser)): Directive0 = {
    hasUserPermissionInProcess(processIdAndUser, Permission.Write)
  }

  def canOverrideUsername(category: String, remoteUserName: Option[RemoteUserName])(
      implicit loggedUser: LoggedUser
  ): Directive0 = {
    Directives.authorize(remoteUserName.isEmpty || loggedUser.can(category, Permission.Impersonate))
  }

  def canOverrideUsername(
      processId: ProcessId,
      remoteUserName: Option[RemoteUserName]
  )(implicit executionContext: ExecutionContext, loggedUser: LoggedUser): Directive0 = {
    Directives.authorizeAsync(
      processAuthorizer.check(processId, Permission.Impersonate, loggedUser).map(_ || remoteUserName.isEmpty)
    )
  }

  private def canInProcess(processId: ProcessId, permission: Permission, user: LoggedUser): Directive0 = {
    Directives.authorizeAsync(_ => processAuthorizer.check(processId, permission, user))
  }

  private def hasUserPermissionInProcess(
      processIdAndUser: (ProcessId, LoggedUser),
      permission: Permission.Value
  ): Directive0 = {
    val (processId, user) = processIdAndUser
    canInProcess(processId, permission, user)
  }

}
