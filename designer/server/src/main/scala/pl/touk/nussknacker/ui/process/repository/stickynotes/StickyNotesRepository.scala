package pl.touk.nussknacker.ui.process.repository.stickynotes

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.LayoutData
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.api.description.stickynotes.Dtos.StickyNote
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.Clock

trait StickyNotesRepository {

  def clock: Clock

  def findStickyNotes(
      scenarioId: ProcessId,
      scenarioVersionId: VersionId
  ): DB[Seq[StickyNote]]

  def addStickyNotes(
      content: String,
      layoutData: LayoutData,
      color: String,
      targetEdge: Option[String],
      scenarioId: ProcessId,
      scenarioVersionId: VersionId
  )(implicit user: LoggedUser): DB[Int]

  def updateStickyNote(
      id: Long,
      content: String,
      layoutData: LayoutData,
      color: String,
      targetEdge: Option[String],
      scenarioVersionId: VersionId,
  )(implicit user: LoggedUser): DB[Int]

  def deleteStickyNote(id: Long)(implicit user: LoggedUser): DB[Int]

}
