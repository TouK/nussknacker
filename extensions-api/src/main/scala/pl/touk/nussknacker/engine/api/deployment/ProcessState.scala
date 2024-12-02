package pl.touk.nussknacker.engine.api.deployment

import io.circe._
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.deployment.{DeploymentId, ExternalDeploymentId}

import java.net.URI

/**
  * Represents status of a scenario.
  * Contains:
  * - status itself and its evaluation moment: status, startTime
  * - how to display in UI: icon, tooltip, description
  * - deployment info: deploymentId, version
  * - which actions are allowed: allowedActions
  * - additional properties: attributes, errors
  *
  * Statuses definition, allowed actions and current scenario ProcessState is defined by [[ProcessStateDefinitionManager]].
  * @param description Short message displayed in top right panel of scenario diagram panel.
  * @param tooltip Message displayed when mouse is hoovering over an icon (both scenarios and diagram panel).
  *                May contain longer, detailed status description.
  */
@JsonCodec case class ProcessState(
    externalDeploymentId: Option[ExternalDeploymentId],
    status: StateStatus,
    version: Option[ProcessVersion],
    visibleActions: List[ScenarioActionName],
    allowedActions: List[ScenarioActionName],
    actionTooltips: Map[ScenarioActionName, String],
    icon: URI,
    tooltip: String,
    description: String,
    startTime: Option[Long],
    attributes: Option[Json],
    errors: List[String]
)

object ProcessState {
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

object StateStatus {
  type StatusName = String

  // StateStatus has to have Decoder defined because it is decoded along with ProcessState in the migration process
  // (see StandardRemoteEnvironment class).
  // In all cases (this one and for FE purposes) only info about the status name is essential. We could encode status
  // just as a String but for compatibility reasons we encode it as a nested object with one, 'name' field
  implicit val statusEncoder: Encoder[StateStatus] = Encoder.encodeString
    .contramap[StateStatus](_.name)
    .mapJson(nameJson => Json.fromFields(Seq("name" -> nameJson)))

  implicit val statusDecoder: Decoder[StateStatus] = Decoder.decodeString.at("name").map(NoAttributesStateStatus)

  // Temporary methods to simplify status creation
  def apply(statusName: StatusName): StateStatus = NoAttributesStateStatus(statusName)

}

trait StateStatus {
  // Status identifier, should be unique among all states registered within all processing types.
  def name: StatusName
}

case class NoAttributesStateStatus(name: StatusName) extends StateStatus {
  override def toString: String = name
}

case class StatusDetails(
    status: StateStatus,
    deploymentId: Option[DeploymentId],
    externalDeploymentId: Option[ExternalDeploymentId] = None,
    version: Option[ProcessVersion] = None,
    startTime: Option[Long] = None,
    attributes: Option[Json] = None,
    errors: List[String] = List.empty
)
