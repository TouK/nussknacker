package pl.touk.nussknacker.restmodel.scenariodetails

import io.circe._
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, ScenarioVersionId}

import java.net.URI
import java.time.Instant

/**
  * Represents status of a scenario.
  * Contains:
  * - status itself and its evaluation moment: status, startTime
  * - how to display in UI: icon, tooltip, description
  * - which actions are allowed: allowedActions
  *
  * Statuses definition, allowed actions and current scenario presentation is defined by [[ProcessStateDefinitionManager]].
  * @param description Short message displayed in top right panel of scenario diagram panel.
  * @param tooltip Message displayed when mouse is hoovering over an icon (both scenarios and diagram panel).
  *                May contain longer, detailed status description.
  */
@JsonCodec case class ScenarioStatusDto(
    status: ScenarioStatusDetailsDto,
    visibleActions: List[ScenarioActionName],
    allowedActions: List[ScenarioActionName],
    actionTooltips: Map[ScenarioActionName, String],
    icon: URI,
    tooltip: String,
    description: String,
)

@JsonCodec case class ScenarioStatusNameWrapperDto(name: String)

sealed trait ScenarioStatusDetailsDto {
  val name: String
}

object ScenarioStatusDetailsDto {

  final case class Running(versionId: String, startedAt: Instant) extends ScenarioStatusDetailsDto {
    override val name: String = "RUNNING"
  }

  final case class NoAttributesStatus(override val name: String) extends ScenarioStatusDetailsDto

  implicit val statusCodec: Codec[ScenarioStatusDetailsDto] = {
    implicit val runningEncoder: Encoder[Running] = Encoder.encodeJson.contramap(status =>
      Json.obj(
        "name"      -> Json.fromString(status.name),
        "versionId" -> Json.fromString(status.versionId),
        "startedAt" -> Encoder[Instant].apply(status.startedAt)
      )
    )

    implicit val runningDecoder: Decoder[Running] = (c: HCursor) => {
      for {
        name      <- c.downField("name").as[String]
        _         <- Either.cond(name == "RUNNING", (), DecodingFailure("Expected name to be RUNNING", c.history))
        version   <- c.downField("versionId").as[String]
        startedAt <- c.downField("startedAt").as[Instant]
      } yield Running(version, startedAt)
    }

    implicit val noAttributesStatusEncoder: Encoder[NoAttributesStatus] = Encoder.forProduct1("name")(_.name)
    implicit val noAttributesStatusDecoder: Decoder[NoAttributesStatus] =
      Decoder.forProduct1("name")(NoAttributesStatus.apply)

    implicit val statusEncoder: Encoder[ScenarioStatusDetailsDto] = Encoder.instance {
      case status: Running            => runningEncoder(status)
      case status: NoAttributesStatus => noAttributesStatusEncoder(status)
    }

    implicit val statusDecoder: Decoder[ScenarioStatusDetailsDto] = {

      Decoder.instance { cursor =>
        cursor.downField("name").as[String].flatMap {
          case "RUNNING" => runningDecoder(cursor)
          case other     => noAttributesStatusDecoder(cursor)
        }
      }
    }
    Codec.from(statusDecoder, statusEncoder)
  }

}

object ScenarioStatusDto {
  implicit val uriEncoder: Encoder[URI]                             = Encoder.encodeString.contramap(_.toString)
  implicit val uriDecoder: Decoder[URI]                             = Decoder.decodeString.map(URI.create)
  implicit val scenarioVersionIdEncoder: Encoder[ScenarioVersionId] = Encoder.encodeLong.contramap(_.value)
  implicit val scenarioVersionIdDecoder: Decoder[ScenarioVersionId] = Decoder.decodeLong.map(ScenarioVersionId.apply)

  implicit val scenarioActionNameEncoder: Encoder[ScenarioActionName] =
    Encoder.encodeString.contramap(_.entryName)
  implicit val scenarioActionNameDecoder: Decoder[ScenarioActionName] =
    Decoder.decodeString.map(ScenarioActionName.withName)

  implicit val scenarioActionNameKeyDecoder: KeyDecoder[ScenarioActionName] =
    (key: String) => Some(ScenarioActionName.withName(key))
  implicit val scenarioActionNameKeyEncoder: KeyEncoder[ScenarioActionName] = _.entryName

}
