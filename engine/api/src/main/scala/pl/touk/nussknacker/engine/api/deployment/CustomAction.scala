package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.process.ProcessName

import java.net.URI

case class CustomAction(name: String,
                        allowedProcessStates: List[StateStatus],
                        icon: Option[URI] = None)

case class CustomActionRequest(name: String,
                               processName: ProcessName,
                               params: Map[String, String])

case class CustomActionResult(msg: String)

case class CustomActionError(msg: String)
