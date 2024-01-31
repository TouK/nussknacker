package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.restmodel.BusinessError.ScenarioNotFoundError
import pl.touk.nussknacker.restmodel.NuException
import pl.touk.nussknacker.restmodel.SecurityError.AuthorizationError
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.initialization.Initialization.nussknackerUser
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class AuthorizeProcess(processRepository: FetchingProcessRepository[Future])(
    implicit executionContext: ExecutionContext
) {

  def check(processId: ProcessId, permission: Permission, user: LoggedUser): Future[Boolean] = {
    processRepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId)
      .map(maybeProcessDetails =>
        maybeProcessDetails
          .map(_.processCategory)
          .exists(user.can(_, permission))
      )
  }

  def check(scenarioName: ProcessName, permission: Permission)(implicit user: LoggedUser): Future[ProcessId] =
    for {
      maybeScenarioId <- processRepository.fetchProcessId(scenarioName)
      isAuthorized <- maybeScenarioId match {
        case Some(scenarioId) =>
          check(scenarioId, permission, user).flatMap {
            case true  => Future.successful(scenarioId)
            case false => Future.failed(NuException(AuthorizationError))
          }
        case None => Future.failed(NuException(ScenarioNotFoundError(scenarioName)))
      }
    } yield isAuthorized

}
