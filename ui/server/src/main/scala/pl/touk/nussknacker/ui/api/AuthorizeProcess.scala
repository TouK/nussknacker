package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.ui.initialization.Initialization.nussknackerUser
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.security.api.Permission.Permission

import scala.concurrent.{ExecutionContext, Future}

class AuthorizeProcess(processRepository:  FetchingProcessRepository[Future])
                      (implicit executionContext: ExecutionContext) {

  def check(processId: ProcessId, permission: Permission, user: LoggedUser):Future[Boolean] = {
    processRepository.fetchLatestProcessDetailsForProcessId[Unit](processId)
      .map(maybeProcessDetails =>
        maybeProcessDetails.map(_.processCategory)
          .exists(user.can(_, permission))
      )
  }
}
