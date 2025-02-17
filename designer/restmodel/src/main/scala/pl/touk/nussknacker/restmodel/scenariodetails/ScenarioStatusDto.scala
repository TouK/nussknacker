package pl.touk.nussknacker.restmodel.scenariodetails

import io.circe._
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, ScenarioVersionId}

import java.net.URI

/**
  * Represents status of a scenario.
  * Contains:
  * - status itself and its evaluation moment: status, startTime
  * - how to display in UI: icon, tooltip, description
  * - which actions are allowed: allowedActions
  * - additional properties: errors
  *
  * Statuses definition, allowed actions and current scenario presentation is defined by [[ProcessStateDefinitionManager]].
  * @param description Short message displayed in top right panel of scenario diagram panel.
  * @param tooltip Message displayed when mouse is hoovering over an icon (both scenarios and diagram panel).
  *                May contain longer, detailed status description.
  */
@JsonCodec case class ScenarioStatusDto(
    // This field is not used by frontend but is useful for scripting
    statusName: String,
    // TODO it is a temporary solution - it should be removed after full migration to statusName
    status: LegacyScenarioStatusNameDto,
    visibleActions: List[ScenarioActionName],
    allowedActions: List[ScenarioActionName],
    actionTooltips: Map[ScenarioActionName, String],
    icon: URI,
    tooltip: String,
    description: String,
    errors: List[String]
)

@JsonCodec case class LegacyScenarioStatusNameDto(name: String)

object ScenarioStatusDto {
  implicit val uriEncoder: Encoder[URI]                             = Encoder.encodeString.contramap(_.toString)
  implicit val uriDecoder: Decoder[URI]                             = Decoder.decodeString.map(URI.create)
  implicit val scenarioVersionIdEncoder: Encoder[ScenarioVersionId] = Encoder.encodeLong.contramap(_.value)
  implicit val scenarioVersionIdDecoder: Decoder[ScenarioVersionId] = Decoder.decodeLong.map(ScenarioVersionId.apply)

  implicit val scenarioActionNameEncoder: Encoder[ScenarioActionName] =
    Encoder.encodeString.contramap(ScenarioActionName.serialize)
  implicit val scenarioActionNameDecoder: Decoder[ScenarioActionName] =
    Decoder.decodeString.map(ScenarioActionName.deserialize)

  implicit val scenarioActionNameKeyDecoder: KeyDecoder[ScenarioActionName] =
    (key: String) => Some(ScenarioActionName.deserialize(key))
  implicit val scenarioActionNameKeyEncoder: KeyEncoder[ScenarioActionName] = (name: ScenarioActionName) =>
    ScenarioActionName.serialize(name)

}
