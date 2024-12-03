package db.migration

import com.typesafe.scalalogging.LazyLogging
import db.migration.V1_060__CreateStickyNotesDefinition.StickyNotesDefinitions
import pl.touk.nussknacker.ui.db.migration.SlickMigration
import slick.jdbc.JdbcProfile
import slick.sql.SqlProfile.ColumnOption.NotNull
import shapeless.syntax.std.tuple._
import java.sql.Timestamp
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

trait V1_060__CreateStickyNotesDefinition extends SlickMigration with LazyLogging {

  import profile.api._

  private val definitions = new StickyNotesDefinitions(profile)

  override def migrateActions: DBIOAction[Any, NoStream, Effect.All] = {
    logger.info("Starting migration V1_060__CreateStickyNotesDefinition")
    for {
      _ <- definitions.stickyNotesEntityTable.schema.create
      _ <-
        sqlu"""ALTER TABLE "sticky_notes" ADD CONSTRAINT "sticky_notes_scenario_version_fk" FOREIGN KEY ("scenario_id", "scenario_version_id") REFERENCES "process_versions" ("process_id", "id") ON DELETE CASCADE;"""
    } yield logger.info("Execution finished for migration V1_060__CreateStickyNotesDefinition")
  }

}

object V1_060__CreateStickyNotesDefinition {

  class StickyNotesDefinitions(val profile: JdbcProfile) {
    import profile.api._
    val stickyNotesEntityTable = TableQuery[StickyNotesEntity]

    class StickyNotesEntity(tag: Tag) extends Table[StickyNoteEventEntityData](tag, "sticky_notes") {

      def id                = column[Long]("id", O.PrimaryKey, O.AutoInc)
      def noteCorrelationId = column[UUID]("note_correlation_id", NotNull)
      def content           = column[String]("content", NotNull)
      def layoutData        = column[String]("layout_data", NotNull)
      def color             = column[String]("color", NotNull)
      def dimensions        = column[String]("dimensions", NotNull)
      def targetEdge        = column[Option[String]]("target_edge")
      def eventCreator      = column[String]("event_creator", NotNull)
      def eventDate         = column[Timestamp]("event_date", NotNull)
      def eventType         = column[String]("event_type", NotNull)
      def scenarioId        = column[Long]("scenario_id", NotNull)
      def scenarioVersionId = column[Long]("scenario_version_id", NotNull)

      def tupleWithoutAutoIncId = (
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
      )

      override def * =
        (id :: tupleWithoutAutoIncId.productElements).tupled <> (
          StickyNoteEventEntityData.apply _ tupled, StickyNoteEventEntityData.unapply
        )

    }

  }

  final case class StickyNoteEventEntityData(
      id: Long,
      noteCorrelationId: UUID,
      content: String,
      layoutData: String,
      color: String,
      dimensions: String,
      targetEdge: Option[String],
      eventCreator: String,
      eventDate: Timestamp,
      eventType: String,
      scenarioId: Long,
      scenarioVersionId: Long
  )

}
