package pl.touk.nussknacker.ui.process.repository.stickynotes

import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.LayoutData
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.api.description.stickynotes.Dtos.StickyNote
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

  // TODO, do we need to add it to DTO?
  private def findCreateEventForNote(noteId: UUID): DB[StickyNoteEventEntityData] = {
    stickyNotesTable
      .filter(event => event.noteId === noteId && event.eventType === StickyNoteEvent.StickyNoteCreated)
      .result
      .headOption
      .flatMap {
        case None =>
          DBIOAction.failed(
            new NoSuchElementException(s"Trying to access not existing StickyNoteCreated event (noteId=$noteId)")
          )
        case Some(value) => DBIOAction.successful(value)
      }
  }

  override def findStickyNotes(scenarioId: ProcessId, scenarioVersionId: VersionId): DB[Seq[StickyNote]] = {
    run(
      stickyNotesTable
        .filter(event =>
          event.scenarioId === scenarioId && event.scenarioVersionId <= scenarioVersionId && event.eventType =!= StickyNoteEvent.StickyNoteDeleted
        )
        .groupBy(_.noteId)
        .map { case (noteId, notes) => (noteId, notes.map(_.eventDate).max) }
        .join(stickyNotesTable)
        .on { case ((noteId, eventDate), event) =>
          event.noteId === noteId && event.eventDate === eventDate
        }
        .map { case ((_, _), event) => event }
        .result
        .map(events => events.map(_.toStickyNote))
    )
  }

  override def addStickyNotes(
      content: String,
      layoutData: LayoutData,
      color: String,
      targetEdge: Option[String],
      scenarioId: ProcessId,
      scenarioVersionId: VersionId
  )(
      implicit user: LoggedUser
  ): DB[Int] = {
    val now = Timestamp.from(clock.instant())
    val entity = StickyNoteEventEntityData(
      id = 0, // ignored since id is AutoInc
      noteId = UUID.randomUUID(),
      content = content,
      layoutData = layoutData,
      color = color,
      targetEdge = targetEdge,
      eventDate = now,
      eventCreator = user.id,
      eventType = StickyNoteEvent.StickyNoteCreated,
      scenarioId = scenarioId,
      scenarioVersionId = scenarioVersionId
    )
    run(stickyNotesTable += entity)
  }

  private def updateStickyNote(id: Long, updateAction: StickyNoteEventEntityData => StickyNoteEventEntityData)(
      implicit user: LoggedUser
  ): DB[Int] = {
    run(for {
      actionResult <- stickyNotesTable.filter(_.id === id).result.headOption.flatMap {
        case None =>
          DBIOAction.failed(
            new NoSuchElementException(s"Trying to update record (id=$id) which is not present in the database")
          )
        case Some(latestEvent) =>
          val newEvent = updateAction(latestEvent)
          stickyNotesTable += newEvent
      }
    } yield actionResult)
  }

  override def updateStickyNote(
      id: Long,
      content: String,
      layoutData: LayoutData,
      color: String,
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
      targetEdge = targetEdge,
      layoutData = layoutData,
      scenarioVersionId = scenarioVersionId
    )
    updateStickyNote(id, updateAction)
  }

  override def deleteStickyNote(id: Long)(implicit user: LoggedUser): DB[Int] = {
    val now = Timestamp.from(clock.instant())
    def updateAction(latestEvent: StickyNoteEventEntityData): StickyNoteEventEntityData = latestEvent.copy(
      eventDate = now,
      eventCreator = user.id,
      eventType = StickyNoteEvent.StickyNoteDeleted
    )
    updateStickyNote(id, updateAction)
  }

}

object DbStickyNotesRepository {

  def create(dbRef: DbRef, clock: Clock)(
      implicit executionContext: ExecutionContext,
  ): StickyNotesRepository = new DbStickyNotesRepository(dbRef, clock)

}
