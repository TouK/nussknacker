package pl.touk.nussknacker.restmodel

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.graph.node.NodeData

@JsonCodec final case class CustomActionRequest(
    actionName: ScenarioActionName,
    params: Map[String, String] = Map.empty
)
