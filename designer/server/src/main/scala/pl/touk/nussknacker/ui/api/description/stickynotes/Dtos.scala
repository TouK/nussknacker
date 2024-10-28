package pl.touk.nussknacker.ui.api.description.stickynotes

import derevo.circe.{decoder, encoder}
import derevo.derive
import pl.touk.nussknacker.engine.api.LayoutData
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import sttp.tapir.derevo.schema

import java.time.Instant
import java.util.UUID

object Dtos {

  @derive(encoder, decoder, schema)
  case class StickyNote(
      id: Long,
      noteId: UUID,
      content: String,
      layoutData: LayoutData,
      color: String,
      targetEdge: Option[String],
      editedBy: String,
      editedAt: Instant
  )

  @derive(encoder, decoder, schema)
  case class StickyNoteRequestBody(
      id: Option[Long],
      scenarioId: ProcessId,
      scenarioVersionId: VersionId,
      content: String,
      layoutData: LayoutData,
      color: String,
      targetEdge: Option[String]
  )

}
