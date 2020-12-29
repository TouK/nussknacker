package pl.touk.nussknacker.ui.api.deployment

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine
import pl.touk.nussknacker.engine.api.process.ProcessName

@JsonCodec
case class CustomActionRequest(actionName: String,
                               params: Option[Map[String, String]] = None) {

  def toEngineRequest(processName: ProcessName): engine.api.deployment.CustomActionRequest =
    engine.api.deployment.CustomActionRequest(
      actionName,
      processName,
      params.getOrElse(Map.empty))
}
