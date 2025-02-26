package pl.touk.nussknacker.ui.db.entity

import io.circe._
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.LayoutData
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.api.description.stickynotes.Dtos.{
  Dimensions,
  StickyNote,
  StickyNoteCorrelationId,
  StickyNoteId
}
import pl.touk.nussknacker.ui.api.description.stickynotes.StickyNoteEvent
import pl.touk.nussknacker.ui.api.description.stickynotes.StickyNoteEvent.StickyNoteEvent
import slick.lifted.{ProvenShape, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp
import java.util.UUID

trait StickyNotesEntityFactory extends BaseEntityFactory {

  import profile.api._

  val processVersionsTable: LTableQuery[ProcessVersionEntityFactory#ProcessVersionEntity]

  class StickyNotesEntity(tag: Tag) extends Table[StickyNoteEventEntityData](tag, "sticky_notes") {

    def id                = column[StickyNoteId]("id", O.PrimaryKey, O.AutoInc)
    def noteCorrelationId = column[StickyNoteCorrelationId]("note_correlation_id", NotNull)
    def content           = column[String]("content", NotNull)
    def layoutData        = column[LayoutData]("layout_data", NotNull)
    def color             = column[String]("color", NotNull)
    def dimensions        = column[Dimensions]("dimensions", NotNull)
    def targetEdge        = column[Option[String]]("target_edge")
    def eventCreator      = column[String]("event_creator", NotNull)
    def eventDate         = column[Timestamp]("event_date", NotNull)
    def eventType         = column[StickyNoteEvent]("event_type", NotNull)
    def scenarioId        = column[ProcessId]("scenario_id", NotNull)
    def scenarioVersionId = column[VersionId]("scenario_version_id", NotNull)

    def * : ProvenShape[StickyNoteEventEntityData] = (
      id,
      noteCorrelationId,
      content,
      layoutData,
      color,
      dimensions,
      targetEdge,
      eventCreator,
      eventDate,
      eventType,
      scenarioId,
      scenarioVersionId
    ) <> (StickyNoteEventEntityData.apply _ tupled, StickyNoteEventEntityData.unapply)

    def scenarioVersion =
      foreignKey("sticky_notes_scenario_version_fk", (scenarioId, scenarioVersionId), processVersionsTable)(
        t => (t.processId, t.id),
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )

  }

  implicit def stickyNoteEventColumnTyped: BaseColumnType[StickyNoteEvent] =
    MappedColumnType.base[StickyNoteEvent, String](_.toString, StickyNoteEvent.withName)

  implicit def stickyNoteIdColumnTyped: BaseColumnType[StickyNoteId] =
    MappedColumnType.base[StickyNoteId, Long](_.value, StickyNoteId(_))
  implicit def stickyNoteCorrelationIdColumnTyped: BaseColumnType[StickyNoteCorrelationId] =
    MappedColumnType.base[StickyNoteCorrelationId, UUID](_.value, StickyNoteCorrelationId(_))

  implicit def layoutDataColumnTyped: BaseColumnType[LayoutData] = MappedColumnType.base[LayoutData, String](
    _.asJson.noSpaces,
    jsonStr =>
      parser.parse(jsonStr).flatMap(Decoder[LayoutData].decodeJson) match {
        case Right(layoutData) => layoutData
        case Left(error)       => throw error
      }
  )

  implicit def dimensionsColumnTyped: BaseColumnType[Dimensions] = MappedColumnType.base[Dimensions, String](
    _.asJson.noSpaces,
    jsonStr =>
      parser.parse(jsonStr).flatMap(Decoder[Dimensions].decodeJson) match {
        case Right(dimensions) => dimensions
        case Left(error)       => throw error
      }
  )

  val stickyNotesTable: LTableQuery[StickyNotesEntityFactory#StickyNotesEntity] = LTableQuery(new StickyNotesEntity(_))

}

final case class StickyNoteEventEntityData(
    id: StickyNoteId,
    noteCorrelationId: StickyNoteCorrelationId,
    content: String,
    layoutData: LayoutData,
    color: String,
    dimensions: Dimensions,
    targetEdge: Option[String],
    eventCreator: String,
    eventDate: Timestamp,
    eventType: StickyNoteEvent,
    scenarioId: ProcessId,
    scenarioVersionId: VersionId
) {
  def toStickyNote: StickyNote =
    StickyNote(id, content, layoutData, color, dimensions, targetEdge, eventCreator, eventDate.toInstant)
}
