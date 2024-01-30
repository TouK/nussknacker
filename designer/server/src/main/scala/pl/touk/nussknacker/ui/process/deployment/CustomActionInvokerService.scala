package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, ScenarioWithDetailsEntity}
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait CustomActionInvokerService {

  def invokeCustomAction(actionName: String, id: ProcessIdWithName, params: Map[String, String])(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): Future[Either[CustomActionError, CustomActionResult]]

}

// TODO: move this logic to DeploymentService - thanks to it, we will be able to:
//       - block two concurrent custom actions - see ManagementResourcesConcurrentSpec
//       - see those actions in the actions table
//       - send notifications about finished/failed custom actions
class CustomActionInvokerServiceImpl(
    processRepository: FetchingProcessRepository[Future],
    dispatcher: DeploymentManagerDispatcher,
    processStateService: ProcessStateService
) extends CustomActionInvokerService {

  override def invokeCustomAction(actionName: String, id: ProcessIdWithName, params: Map[String, String])(
      implicit user: LoggedUser,
      ec: ExecutionContext
  ): Future[Either[CustomActionError, CustomActionResult]] = {

    def createCustomAction(process: ScenarioWithDetailsEntity[_]) =
      engine.api.deployment.CustomActionRequest(
        name = actionName,
        processVersion = process.toEngineProcessVersion,
        user = user.toManagerUser,
        params = params
      )

    val maybeProcess = processRepository.fetchLatestProcessDetailsForProcessId[CanonicalProcess](id.id)
    maybeProcess.flatMap {
      case Some(process) if process.isFragment =>
        actionError(
          CustomActionForbidden(createCustomAction(process), "Invoke custom action on fragment is forbidden.")
        )
      case Some(process) if process.isArchived =>
        actionError(
          CustomActionForbidden(createCustomAction(process), "Invoke custom action on archived scenario is forbidden.")
        )
      case Some(process) =>
        val actionReq = createCustomAction(process)
        val manager   = dispatcher.deploymentManagerUnsafe(process.processingType)
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
        Future.failed(ProcessNotFoundError(id.name))
    }
  }

  // FIXME: change returning successful to failed..
  private def actionError(error: CustomActionError) = Future.successful(Left(error))

}
