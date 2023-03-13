package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait CustomActionInvokerService {

  def invokeCustomAction(actionName: String, id: ProcessIdWithName, params: Map[String, String])
                        (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Either[CustomActionError, CustomActionResult]]

}

// TODO: move this logic to DeploymentService - thanks to it, we will be able to:
//       - block two concurrent custom actions - see ManagementResourcesConcurrentSpec
//       - see those actions in the actions table
//       - send notifications about finished/failed custom actions
class CustomActionInvokerServiceImpl(processRepository: FetchingProcessRepository[Future],
                                     dispatcher: DeploymentManagerDispatcher,
                                     processStateService: ProcessStateService) extends CustomActionInvokerService {
  override def invokeCustomAction(actionName: String, id: ProcessIdWithName, params: Map[String, String])
                                 (implicit user: LoggedUser, ec: ExecutionContext): Future[Either[CustomActionError, CustomActionResult]] = {
    val maybeProcess = processRepository.fetchLatestProcessDetailsForProcessId[CanonicalProcess](id.id)
    maybeProcess.flatMap {
      case Some(process) =>
        val actionReq = engine.api.deployment.CustomActionRequest(
          name = actionName,
          processVersion = process.toEngineProcessVersion,
          user = user.toManagerUser,
          params = params)
        val manager = dispatcher.deploymentManager(process.processingType)
        manager.customActions.find(_.name == actionName) match {
          case Some(customAction) =>
            implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
            processStateService.getProcessState(id).flatMap { status =>
              if (customAction.allowedStateStatusNames.contains(status.status.name)) {
                manager.invokeCustomAction(actionReq, process.json)
              } else
                Future.successful(Left(CustomActionInvalidStatus(actionReq, status.status.name)))
            }
          case None =>
            Future.successful(Left(CustomActionNonExisting(actionReq)))
        }
      case None =>
        Future.failed(ProcessNotFoundError(id.id.value.toString))
    }
  }

}
