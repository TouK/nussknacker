package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.process.ProcessId
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import pl.touk.nussknacker.ui.security.api.PermissionSyntax._

import scala.concurrent.{ExecutionContext, Future}

class AuthorizeProcess(processRepository:  FetchingProcessRepository)
                      (implicit executionContext: ExecutionContext) {

  def check(processId: ProcessId, permission: Permission, user:LoggedUser):Future[Boolean] = {
    processRepository.fetchLatestProcessDetailsForProcessId(processId)(user, implicitly[ExecutionContext])
      .map(maybeProcessDetails =>
        maybeProcessDetails.map(_.processCategory)
          .exists(user.can(_, permission))
      )
  }
}
