package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.{BadRequestError, EspError, ForbiddenError, NotFoundError}
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait CustomActionInvokerService {

  def invokeCustomAction(actionName: String, id: ProcessIdWithName, params: Map[String, String])
                        (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[CustomActionResult]

}

// TODO: move this logic to DeploymentService - thanks to it, we will be able to:
//       - block two concurrent custom actions - see ManagementResourcesConcurrentSpec
//       - see those actions in the actions table
//       - send notifications about finished/failed custom actions
class CustomActionInvokerServiceImpl(processRepository: FetchingProcessRepository[Future],
                                     dispatcher: DeploymentManagerDispatcher,
                                     processStateService: ProcessStateService) extends CustomActionInvokerService {
  override def invokeCustomAction(actionName: String, id: ProcessIdWithName, params: Map[String, String])
                                 (implicit user: LoggedUser, ec: ExecutionContext): Future[CustomActionResult] = {
    val maybeProcess = processRepository.fetchLatestProcessDetailsForProcessId[CanonicalProcess](id.id)
    maybeProcess.flatMap {
      case Some(process) if process.isFragment =>
        actionError(CustomActionOnFragmentForbidden)
      case Some(process) if process.isArchived =>
        actionError(CustomActionOnArchivedForbidden)
      case Some(process) =>
        val manager = dispatcher.deploymentManagerUnsafe(process.processingType)
        val actionReq = CustomActionRequest(
          name = actionName,
          processVersion = process.toEngineProcessVersion,
          user = user.toManagerUser,
          params = params
        )
        manager.customActions.find(_.name == actionName) match {
          case Some(customAction) =>
            implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
            processStateService.getProcessState(process).flatMap { status =>
              if (customAction.allowedStateStatusNames.contains(status.status.name)) {
                manager.invokeCustomAction(actionReq, process.json)
              } else
                actionError(CustomActionInvalidStatus(actionReq, status.status.name))
            }
          case None =>
            actionError(CustomActionNonExisting(actionReq))
        }
      case _ =>
        Future.failed(ProcessNotFoundError(id.id.value.toString))
    }
  }

  private def actionError(error: Exception with EspError): Future[CustomActionResult] = Future.failed(error)

}

case class CustomActionInvalidStatus(request: CustomActionRequest, stateStatusName: String)
  extends Exception(s"""Invalid scenario status: $stateStatusName is not allowed for action "${request.name}"""") with BadRequestError

case class CustomActionNonExisting(request: CustomActionRequest)
  extends Exception(s"""Action "${request.name}" does not exist""") with NotFoundError

case object CustomActionOnFragmentForbidden
  extends Exception("Invoke custom action on fragment is forbidden.") with ForbiddenError

case object CustomActionOnArchivedForbidden
  extends Exception("Invoke custom action on archived scenario is forbidden.") with ForbiddenError