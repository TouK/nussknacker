package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.process.ProcessName

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}

object CustomAction {

  def invokeWithValidation(actionRequest: CustomActionRequest, manager: ProcessManager)
                          (implicit ec: ExecutionContext): Future[Either[CustomActionError, CustomActionResult]] = {
    manager.customActions.find(_.name == actionRequest.name) match {
      case Some(action) =>
        manager.findJobStatus(actionRequest.processName).flatMap {
          case None => Future(Left(CustomActionError("Couldn't get process status")))
          case Some(state) if action.allowedProcessStates.contains(state.status) => manager.invokeCustomAction(actionRequest)
          case _ => Future(Left(CustomActionError("Invalid process status")))
        }
      case None => Future(Left(CustomActionError("Invalid custom action")))
    }
  }
}

case class CustomAction(name: String,
                        allowedProcessStates: List[StateStatus],
                        icon: Option[URI] = None)

case class CustomActionRequest(name: String,
                               processName: ProcessName,
                               params: Map[String, String])

case class CustomActionResult(msg: String)

case class CustomActionError(msg: String)
