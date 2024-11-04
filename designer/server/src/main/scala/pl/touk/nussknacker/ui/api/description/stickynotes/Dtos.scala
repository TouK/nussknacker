package pl.touk.nussknacker.ui.api.description.stickynotes

import derevo.circe.{decoder, encoder}
import derevo.derive
import pl.touk.nussknacker.engine.api.LayoutData
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import sttp.tapir.{Codec, CodecFormat, Schema}
import sttp.tapir.derevo.schema

import java.time.Instant
import java.util.UUID

object Dtos {
  import pl.touk.nussknacker.ui.api.TapirCodecs.VersionIdCodec.{schema => versionIdSchema}

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
      scenarioVersionId: VersionId,
      content: String,
      layoutData: LayoutData,
      color: String,
      targetEdge: Option[String]
  )

  sealed trait StickyNotesError

  implicit lazy val cellErrorSchema: Schema[LayoutData] = Schema.derived

  object StickyNotesError {

    final case class NoScenario(scenarioName: ProcessName) extends StickyNotesError
    final case object NoPermission                         extends StickyNotesError with CustomAuthorizationError
    final case class StickyNoteContentTooLong(count: Int, max: Int) extends StickyNotesError

    implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoScenario](e => s"No scenario ${e.scenarioName} found")

    implicit val noCommentCodec: Codec[String, StickyNoteContentTooLong, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[StickyNoteContentTooLong](e =>
        s"Provided note content is too long (${e.count} characters). Max content length is ${e.max} "
      )

  }

}
