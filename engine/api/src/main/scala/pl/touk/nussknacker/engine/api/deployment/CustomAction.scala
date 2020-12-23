package pl.touk.nussknacker.engine.api.deployment

case class CustomAction(name: String,
                        processId: Long,
                        params: Map[String, String])

case class CustomActionResult(msg: String)

case class CustomActionError(msg: String)
