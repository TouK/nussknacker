package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion

import java.net.URI

/*
This is an experimental version, API will change in the future.
CustomActions purpose is to allow non standard process management actions (like deploy, cancel, etc.)

FIXME:
1. Additional validations on action invoke, like checking if process definition is valid
2. Handle CustomActionRequest#params

Things to consider in future changes:
1. Allowing for attaching comments to custom actions, similarly to stop/deploy comments.
2. Storing custom actions taken in DB.
 */

case class CustomAction(name: String,
                        allowedProcessStates: List[StateStatus],
                        icon: Option[URI] = None)

case class CustomActionRequest(name: String,
                               processVersion: ProcessVersion,
                               user: User,
                               params: Map[String, String])

case class CustomActionResult(req: CustomActionRequest, msg: String)

sealed trait CustomActionError extends Exception {
  def request: CustomActionRequest

  def msg: String

  override def getMessage: String = msg
}

case class CustomActionFailure(request: CustomActionRequest, msg: String) extends CustomActionError

case class CustomActionInvalidStatus(request: CustomActionRequest, stateStatus: StateStatus) extends CustomActionError {
  override val msg: String = s"Process status: ${stateStatus.name} is not allowed for action ${request.name}"
}

case class CustomActionNotImplemented(request: CustomActionRequest) extends CustomActionError {
  override val msg: String = s"${request.name} is not implemented"
}

case class CustomActionNonExisting(request: CustomActionRequest) extends CustomActionError {
  override val msg: String = s"${request.name} is not existing"
}
