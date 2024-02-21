package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName

//TODO: how is this different than ActionRequest
@JsonCodec
final case class ActionRequest(actionName: ScenarioActionName, params: Option[Map[String, String]] = None)
