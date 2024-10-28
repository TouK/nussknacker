package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.engine.api.LayoutData
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.api.description.stickynotes.StickyNoteEvent
import pl.touk.nussknacker.ui.api.description.stickynotes.StickyNoteEvent.StickyNoteEvent
import slick.lifted.{TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull
import io.circe.syntax._
import io.circe._
import pl.touk.nussknacker.ui.api.description.stickynotes.Dtos.StickyNote

import java.sql.Timestamp
import java.util.UUID

trait StickyNotesEntityFactory extends BaseEntityFactory {

  import profile.api._

  val processesTable: LTableQuery[ProcessEntityFactory#ProcessEntity]
  val processVersionsTable: LTableQuery[ProcessVersionEntityFactory#ProcessVersionEntity]

  implicit val stickyNoteEventColumnTyped: BaseColumnType[StickyNoteEvent] =
    MappedColumnType.base[StickyNoteEvent, String](_.toString, StickyNoteEvent.withName)

  implicit val layoutDataColumnTyped: BaseColumnType[LayoutData] = MappedColumnType.base[LayoutData, String](
    _.asJson.noSpaces,
    jsonStr =>
      parser.parse(jsonStr).flatMap(Decoder[LayoutData].decodeJson) match {
        case Right(layoutData) => layoutData
        case Left(error)       => throw error
      }
  )

  class StickyNotesEntity(tag: Tag) extends Table[StickyNoteEventEntityData](tag, "sticky_notes") {

    def id                = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def noteId            = column[UUID]("note_id", NotNull)
    def content           = column[String]("content", NotNull)
    def layoutData        = column[LayoutData]("layout_data", NotNull)
    def color             = column[String]("color", NotNull)
    def targetEdge        = column[Option[String]]("target_edge", O.Default(null))
    def eventCreator      = column[String]("event_creator", NotNull)
    def eventDate         = column[Timestamp]("event_date", NotNull)
    def eventType         = column[StickyNoteEvent]("event_type", NotNull)
    def scenarioId        = column[ProcessId]("scenario_id", NotNull)
    def scenarioVersionId = column[VersionId]("scenario_version_id", NotNull)

    def * = (
      id,
      noteId,
      content,
      layoutData,
      color,
      targetEdge,
      eventCreator,
      eventDate,
      eventType,
      scenarioId,
      scenarioVersionId
    ) <> (StickyNoteEventEntityData.apply _ tupled, StickyNoteEventEntityData.unapply)

    def pk = primaryKey("pk_sticky_notes", id)

    def scenario = foreignKey("sticky_notes-scenario-fk", scenarioId, processesTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

    def scenarioVersion = foreignKey("sticky_notes-scenario-version-fk", scenarioVersionId, processVersionsTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

  }

  val stickyNotesTable: LTableQuery[StickyNotesEntityFactory#StickyNotesEntity] = LTableQuery(new StickyNotesEntity(_))

}

final case class StickyNoteEventEntityData(
    id: Long,
    noteId: UUID,
    content: String,
    layoutData: LayoutData,
    color: String,
    targetEdge: Option[String],
    eventCreator: String,
    eventDate: Timestamp,
    eventType: StickyNoteEvent,
    scenarioId: ProcessId,
    scenarioVersionId: VersionId
) {
  def toStickyNote: StickyNote =
    StickyNote(id, noteId, content, layoutData, color, targetEdge, eventCreator, eventDate.toInstant)
}
