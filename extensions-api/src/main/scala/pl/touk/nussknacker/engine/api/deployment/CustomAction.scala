package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.deployment.User

import java.net.URI

/*
This is an experimental version, API will change in the future.
CustomActions purpose is to allow non standard process management actions (like deploy, cancel, etc.)

FIXME:
1. Additional validations on action invoke, like checking if process definition is valid

Things to consider in future changes:
1. Allowing for attaching comments to custom actions, similarly to stop/deploy comments.
2. Storing custom actions taken in DB.
 */

case class CustomAction(
    name: String,
    // We cannot use "engine.api.deployment.StateStatus" because it can be implemented as a class containing nonconstant attributes
    allowedStateStatusNames: List[String],
    parameters: List[CustomActionParameter] = Nil,
    icon: Option[URI] = None
)

//TODO: validators?
case class CustomActionParameter(
    name: String,
    editor: Option[ParameterEditor],
    validators: Option[List[ParameterValidator]]
)

case class CustomActionRequest(name: String, processVersion: ProcessVersion, user: User, params: Map[String, String])

case class CustomActionResult(req: CustomActionRequest, msg: String)
