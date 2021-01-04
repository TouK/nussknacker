package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.process.ProcessName

import java.net.URI

/*
This is a work in progress version.
FIXME:
1. Currently custom actions definition is split between ProcessManager and ProcessStateDefinitionManager,
   the latter specifying which custom actions are available and the former defining how custom action should be handled.
2. There is no validation on BE to check if given action can be processed,
   eg if an action is some sort of custom process deployment we should validate it against current process status.
   For now we only disable custom action buttons on FE when action's allowed process states doesn't include current process state.

Things to consider in future changes:
1. Allowing for attaching comments to custom actions, similarly to stop/deploy comments.
2. Storing custom actions taken in DB.
 */

case class CustomAction(name: String,
                        allowedProcessStates: List[StateStatus],
                        icon: Option[URI] = None)

case class CustomActionRequest(name: String,
                               processName: ProcessName,
                               params: Map[String, String])

case class CustomActionResult(msg: String)

case class CustomActionError(msg: String)
