package pl.touk.nussknacker.engine.deployment

import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.api.deployment.{ProcessState, ScenarioActionName, StateStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}

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

case class CustomActionDefinition(
    actionName: ScenarioActionName,
    // We cannot use "engine.api.deployment.StateStatus" because it can be implemented as a class containing nonconstant attributes
    // allowedStateStatusNames: List[StateStatus.StatusName],
    parameters: List[CustomActionParameter],
    icon: Option[URI],
    allowed: CustomActionContext => Boolean
)

object CustomActionDefinition {

  def apply(
      actionName: ScenarioActionName,
      // We cannot use "engine.api.deployment.StateStatus" because it can be implemented as a class containing nonconstant attributes
      allowedStateStatusNames: List[StateStatus.StatusName],
      parameters: List[CustomActionParameter] = Nil,
      icon: Option[URI] = None,
      allowed: CustomActionContext => Boolean = _ => false
  ): CustomActionDefinition =
    CustomActionDefinition(
      actionName = actionName,
      parameters = parameters,
      icon = icon,
      allowed = ctx => allowedStateStatusNames.contains(ctx.processState.status.name)
    )

}

case class CustomActionParameter(
    name: String,
    editor: ParameterEditor,
    validators: List[ParameterValidator] = Nil
)

case class CustomActionResult(msg: String)

case class CustomActionContext(
    processName: ProcessName,
    scenarioVersion: VersionId,
    processState: ProcessState
)
