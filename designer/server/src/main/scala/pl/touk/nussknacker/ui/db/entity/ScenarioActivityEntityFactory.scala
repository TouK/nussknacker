package pl.touk.nussknacker.ui.db.entity

import enumeratum.EnumEntry.UpperSnakecase
import enumeratum._
import io.circe.Decoder
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.modelinfo.ModelInfo
import pl.touk.nussknacker.engine.api.deployment.ProcessActionState.ProcessActionState
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessId
import slick.lifted.{TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp
import java.util.UUID
import scala.collection.immutable
import scala.util.matching.Regex

trait ScenarioActivityEntityFactory extends BaseEntityFactory {

  import profile.api._

  val scenarioActivityTable: LTableQuery[ScenarioActivityEntityFactory#ScenarioActivityEntity] = LTableQuery(
    new ScenarioActivityEntity(_)
  )

  class ScenarioActivityEntity(tag: Tag) extends Table[ScenarioActivityEntityData](tag, "scenario_activities") {

    def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def activityType: Rep[ScenarioActivityType] = column[ScenarioActivityType]("activity_type", NotNull)

    def scenarioId: Rep[ProcessId] = column[ProcessId]("scenario_id", NotNull)

    def activityId: Rep[ScenarioActivityId] = column[ScenarioActivityId]("activity_id", NotNull, O.Unique)

    def userId: Rep[Option[String]] = column[Option[String]]("user_id")

    def userName: Rep[String] = column[String]("user_name", NotNull)

    def impersonatedByUserId: Rep[Option[String]] = column[Option[String]]("impersonated_by_user_id")

    def impersonatedByUserName: Rep[Option[String]] = column[Option[String]]("impersonated_by_user_name")

    def lastModifiedByUserName: Rep[Option[String]] = column[Option[String]]("last_modified_by_user_name")

    def lastModifiedAt: Rep[Option[Timestamp]] = column[Option[Timestamp]]("last_modified_at")

    def createdAt: Rep[Timestamp] = column[Timestamp]("created_at", NotNull)

    def scenarioVersion: Rep[Option[ScenarioVersionId]] = column[Option[ScenarioVersionId]]("scenario_version")

    def comment: Rep[Option[String]] = column[Option[String]]("comment")

    def attachmentId: Rep[Option[Long]] = column[Option[Long]]("attachment_id")

    def performedAt: Rep[Option[Timestamp]] = column[Option[Timestamp]]("performed_at")

    def state: Rep[Option[ProcessActionState]] = column[Option[ProcessActionState]]("state")

    def errorMessage: Rep[Option[String]] = column[Option[String]]("error_message")

    def modelInfo: Rep[Option[ModelInfo]] = column[Option[ModelInfo]]("model_info")

    def additionalProperties: Rep[AdditionalProperties] = column[AdditionalProperties]("additional_properties")

    override def * =
      (
        id,
        activityType,
        scenarioId,
        activityId,
        userId,
        userName,
        impersonatedByUserId,
        impersonatedByUserName,
        lastModifiedByUserName,
        lastModifiedAt,
        createdAt,
        scenarioVersion,
        comment,
        attachmentId,
        performedAt,
        state,
        errorMessage,
        modelInfo,
        additionalProperties,
      ) <> (
        ScenarioActivityEntityData.apply _ tupled, ScenarioActivityEntityData.unapply
      )

  }

  implicit def scenarioActivityTypeMapper: BaseColumnType[ScenarioActivityType] =
    MappedColumnType.base[ScenarioActivityType, String](
      _.entryName,
      name =>
        ScenarioActivityType
          .withEntryNameOption(name)
          .getOrElse(throw new IllegalArgumentException(s"Invalid ScenarioActivityType $name"))
    )

  implicit def scenarioIdMapper: BaseColumnType[ScenarioId] =
    MappedColumnType.base[ScenarioId, Long](_.value, ScenarioId.apply)

  implicit def scenarioActivityIdMapper: BaseColumnType[ScenarioActivityId] =
    MappedColumnType.base[ScenarioActivityId, UUID](_.value, ScenarioActivityId.apply)

  implicit def scenarioVersionIdMapper: BaseColumnType[ScenarioVersionId] =
    MappedColumnType.base[ScenarioVersionId, Long](_.value, ScenarioVersionId.apply)

  implicit def additionalPropertiesMapper: BaseColumnType[AdditionalProperties] =
    MappedColumnType.base[AdditionalProperties, String](
      _.properties.asJson.noSpaces,
      jsonStr =>
        io.circe.parser.parse(jsonStr).flatMap(Decoder[Map[String, String]].decodeJson) match {
          case Right(rawParams) => AdditionalProperties(rawParams)
          case Left(error)      => throw error
        }
    )

}

sealed trait ScenarioActivityType extends EnumEntry with UpperSnakecase

object ScenarioActivityType extends Enum[ScenarioActivityType] {

  case object ScenarioCreated             extends ScenarioActivityType
  case object ScenarioArchived            extends ScenarioActivityType
  case object ScenarioUnarchived          extends ScenarioActivityType
  case object ScenarioDeployed            extends ScenarioActivityType
  case object ScenarioPaused              extends ScenarioActivityType
  case object ScenarioCanceled            extends ScenarioActivityType
  case object ScenarioModified            extends ScenarioActivityType
  case object ScenarioNameChanged         extends ScenarioActivityType
  case object CommentAdded                extends ScenarioActivityType
  case object AttachmentAdded             extends ScenarioActivityType
  case object ChangedProcessingMode       extends ScenarioActivityType
  case object IncomingMigration           extends ScenarioActivityType
  case object OutgoingMigration           extends ScenarioActivityType
  case object PerformedSingleExecution    extends ScenarioActivityType
  case object PerformedScheduledExecution extends ScenarioActivityType
  case object AutomaticUpdate             extends ScenarioActivityType

  final case class CustomAction(value: String) extends ScenarioActivityType {
    override def entryName: String = s"CUSTOM_ACTION_[$value]"
  }

  object CustomAction {
    private val pattern: Regex = """CUSTOM_ACTION_\[(.+?)]""".r

    def withEntryNameOption(entryName: String): Option[CustomAction] = {
      entryName match {
        case pattern(value) => Some(CustomAction(value))
        case _              => None
      }
    }

  }

  def withEntryNameOption(entryName: String): Option[ScenarioActivityType] =
    withNameOption(entryName)
      .orElse(CustomAction.withEntryNameOption(entryName))

  override def values: immutable.IndexedSeq[ScenarioActivityType] = findValues

}

final case class AdditionalProperties(properties: Map[String, String]) {

  def withProperty(key: String, value: String): AdditionalProperties = {
    AdditionalProperties(properties ++ Map((key, value)))
  }

}

object AdditionalProperties {
  def empty: AdditionalProperties = AdditionalProperties(Map.empty)
}

final case class ScenarioActivityEntityData(
    id: Long,
    activityType: ScenarioActivityType,
    scenarioId: ProcessId,
    activityId: ScenarioActivityId,
    userId: Option[String],
    userName: String,
    impersonatedByUserId: Option[String],
    impersonatedByUserName: Option[String],
    lastModifiedByUserName: Option[String],
    lastModifiedAt: Option[Timestamp],
    createdAt: Timestamp,
    scenarioVersion: Option[ScenarioVersionId],
    comment: Option[String],
    attachmentId: Option[Long],
    finishedAt: Option[Timestamp],
    state: Option[ProcessActionState],
    errorMessage: Option[String],
    modelInfo: Option[ModelInfo],
    additionalProperties: AdditionalProperties,
)
