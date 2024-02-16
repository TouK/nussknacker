package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName

@JsonCodec
final case class CustomActionRequest(actionName: ScenarioActionName, params: Option[Map[String, String]] = None)
