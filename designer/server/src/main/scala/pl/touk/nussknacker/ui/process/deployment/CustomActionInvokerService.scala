package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.User
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class CustomActionInvokerService(processRepository: FetchingProcessRepository[Future],
                                 dispatcher: DeploymentManagerDispatcher,
                                 processStateService: ProcessStateService) {
  def invokeCustomAction(actionName: String, id: ProcessIdWithName, params: Map[String, String])
                        (implicit user: LoggedUser, ec: ExecutionContext): Future[Either[CustomActionError, CustomActionResult]] = {
    val maybeProcess = processRepository.fetchLatestProcessDetailsForProcessId[CanonicalProcess](id.id)
    maybeProcess.flatMap {
      case Some(process) =>
        val actionReq = engine.api.deployment.CustomActionRequest(
          name = actionName,
          processVersion = process.toEngineProcessVersion,
          user = toManagerUser(user),
          params = params)
        dispatcher.deploymentManager(id.id).flatMap { manager =>
          manager.customActions.find(_.name == actionName) match {
            case Some(customAction) =>
              processStateService.getProcessState(id).flatMap(status => {
                if (customAction.allowedStateStatusNames.contains(status.status.name)) {
                  manager.invokeCustomAction(actionReq, process.json)
                } else
                  Future(Left(CustomActionInvalidStatus(actionReq, status.status.name)))
              })
            case None =>
              Future(Left(CustomActionNonExisting(actionReq)))
          }
        }
      case None =>
        Future.failed(ProcessNotFoundError(id.id.value.toString))
    }
  }

  private def toManagerUser(loggedUser: LoggedUser) = User(loggedUser.id, loggedUser.username)

}
