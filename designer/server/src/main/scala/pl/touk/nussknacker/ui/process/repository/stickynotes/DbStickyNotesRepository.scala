package pl.touk.nussknacker.ui.process.repository.stickynotes

import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.LayoutData
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.api.description.stickynotes.Dtos.{
  Dimensions,
  StickyNote,
  StickyNoteCorrelationId,
  StickyNoteId
}
import pl.touk.nussknacker.ui.api.description.stickynotes.StickyNoteEvent
import pl.touk.nussknacker.ui.db.entity.StickyNoteEventEntityData
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.process.repository.DbioRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import slick.dbio.DBIOAction

import java.sql.Timestamp
import java.time.Clock
import java.util.UUID
import scala.concurrent.ExecutionContext

class DbStickyNotesRepository private (override protected val dbRef: DbRef, override val clock: Clock)(
    implicit executionContext: ExecutionContext
) extends DbioRepository
    with NuTables
    with StickyNotesRepository
    with LazyLogging {

  import profile.api._

  override def findStickyNotes(scenarioId: ProcessId, scenarioVersionId: VersionId): DB[Seq[StickyNote]] = {
    run(
      stickyNotesTable
        .filter(event => event.scenarioId === scenarioId && event.scenarioVersionId <= scenarioVersionId)
        .groupBy(_.noteCorrelationId)
        .map { case (noteCorrelationId, notes) => (noteCorrelationId, notes.map(_.eventDate).max) }
        .join(stickyNotesTable)
        .on { case ((noteCorrelationId, eventDate), event) =>
          event.noteCorrelationId === noteCorrelationId && event.eventDate === eventDate
        }
        .map { case ((_, _), event) => event }
        .result
        .map(events => events.filter(_.eventType != StickyNoteEvent.StickyNoteDeleted).map(_.toStickyNote))
    )
  }

  override def countStickyNotes(scenarioId: ProcessId, scenarioVersionId: VersionId): DB[Int] = {
    run(
      stickyNotesTable
        .filter(event => event.scenarioId === scenarioId && event.scenarioVersionId <= scenarioVersionId)
        .groupBy(_.noteCorrelationId)
        .map { case (noteCorrelationId, notes) => (noteCorrelationId, notes.map(_.eventDate).max) }
        .join(stickyNotesTable)
        .on { case ((noteCorrelationId, eventDate), event) =>
          event.noteCorrelationId === noteCorrelationId && event.eventDate === eventDate
        }
        .map { case ((_, _), event) => event }
        .result
        .map(events => events.count(_.eventType != StickyNoteEvent.StickyNoteDeleted))
    )
  }

  override def findStickyNoteById(
      noteId: StickyNoteId
  )(implicit user: LoggedUser): DB[Option[StickyNoteEventEntityData]] = {
    run(
      stickyNotesTable
        .filter(_.id === noteId)
        .result
        .headOption
    )
  }

  override def addStickyNote(
      content: String,
      layoutData: LayoutData,
      color: String,
      dimensions: Dimensions,
      targetEdge: Option[String],
      scenarioId: ProcessId,
      scenarioVersionId: VersionId
  )(
      implicit user: LoggedUser
  ): DB[StickyNoteCorrelationId] = {
    val now = Timestamp.from(clock.instant())
    val entity = StickyNoteEventEntityData(
      id = StickyNoteId(0), // ignored since id is AutoInc
      noteCorrelationId = StickyNoteCorrelationId(UUID.randomUUID()),
      content = content,
      layoutData = layoutData,
      color = color,
      dimensions = dimensions,
      targetEdge = targetEdge,
      eventDate = now,
      eventCreator = user.id,
      eventType = StickyNoteEvent.StickyNoteCreated,
      scenarioId = scenarioId,
      scenarioVersionId = scenarioVersionId
    )
    run(stickyNotesTable += entity).flatMap {
      case 0 => DBIOAction.failed(new IllegalStateException(s"This is odd, no sticky note was added"))
      case 1 => DBIOAction.successful(entity.noteCorrelationId)
      case n =>
        DBIOAction.failed(
          new IllegalStateException(s"This is odd, more than one sticky note were added (added $n records).")
        )
    }

  }

  private def updateStickyNote(id: StickyNoteId, updateAction: StickyNoteEventEntityData => StickyNoteEventEntityData)(
      implicit user: LoggedUser
  ): DB[Int] = {
    run(for {
      actionResult <- stickyNotesTable.filter(_.id === id).result.headOption.flatMap {
        case None =>
          DBIOAction.failed(
            new NoSuchElementException(s"Trying to update record (id=${id.value}) which is not present in the database")
          )
        case Some(latestEvent) =>
          val newEvent = updateAction(latestEvent)
          stickyNotesTable += newEvent
      }
    } yield actionResult)
  }

  override def updateStickyNote(
      noteId: StickyNoteId,
      content: String,
      layoutData: LayoutData,
      color: String,
      dimensions: Dimensions,
      targetEdge: Option[String],
      scenarioVersionId: VersionId,
  )(
      implicit user: LoggedUser
  ): DB[Int] = {
    val now = Timestamp.from(clock.instant())
    def updateAction(latestEvent: StickyNoteEventEntityData): StickyNoteEventEntityData = latestEvent.copy(
      eventDate = now,
      eventCreator = user.id,
      eventType = StickyNoteEvent.StickyNoteUpdated,
      content = content,
      color = color,
      dimensions = dimensions,
      targetEdge = targetEdge,
      layoutData = layoutData,
      scenarioVersionId = scenarioVersionId
    )
    updateStickyNote(noteId, updateAction)
  }

  override def deleteStickyNote(noteId: StickyNoteId)(implicit user: LoggedUser): DB[Int] = {
    val now = Timestamp.from(clock.instant())
    def updateAction(latestEvent: StickyNoteEventEntityData): StickyNoteEventEntityData = latestEvent.copy(
      eventDate = now,
      eventCreator = user.id,
      eventType = StickyNoteEvent.StickyNoteDeleted
    )
    updateStickyNote(noteId, updateAction)
  }

}

object DbStickyNotesRepository {

  def create(dbRef: DbRef, clock: Clock)(
      implicit executionContext: ExecutionContext,
  ): StickyNotesRepository = new DbStickyNotesRepository(dbRef, clock)

}
