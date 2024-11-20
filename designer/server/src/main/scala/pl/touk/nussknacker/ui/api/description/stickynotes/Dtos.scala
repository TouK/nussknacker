package pl.touk.nussknacker.ui.api.description.stickynotes

import derevo.circe.{decoder, encoder}
import derevo.derive
import io.circe.{Decoder, Encoder}
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

  final case class StickyNoteId(value: Long) extends AnyVal

  object StickyNoteId {
    implicit val encoder: Encoder[StickyNoteId] = Encoder.encodeLong.contramap(_.value)
    implicit val decoder: Decoder[StickyNoteId] = Decoder.decodeLong.map(StickyNoteId(_))
  }

  final case class StickyNoteCorrelationId(value: UUID) extends AnyVal

  object StickyNoteCorrelationId {
    implicit val encoder: Encoder[StickyNoteCorrelationId] = Encoder.encodeUUID.contramap(_.value)
    implicit val decoder: Decoder[StickyNoteCorrelationId] = Decoder.decodeUUID.map(StickyNoteCorrelationId(_))
  }

  implicit lazy val stickyNoteCorrelationIdSchema: Schema[StickyNoteCorrelationId] =
    Schema.schemaForUUID.as[StickyNoteCorrelationId]
  implicit lazy val stickyNoteIdSchema: Schema[StickyNoteId] = Schema.schemaForLong.as[StickyNoteId]

  @derive(encoder, decoder, schema)
  case class Dimensions(
      width: Long,
      height: Long
  )

  @derive(encoder, decoder, schema)
  case class StickyNote(
      noteId: StickyNoteId,
      content: String,
      layoutData: LayoutData,
      color: String,
      dimensions: Dimensions,
      targetEdge: Option[String],
      editedBy: String,
      editedAt: Instant
  )

  @derive(encoder, decoder, schema)
  case class StickyNoteAddRequest(
      scenarioVersionId: VersionId,
      content: String,
      layoutData: LayoutData,
      color: String,
      dimensions: Dimensions,
      targetEdge: Option[String]
  )

  @derive(encoder, decoder, schema)
  case class StickyNoteUpdateRequest(
      noteId: StickyNoteId,
      scenarioVersionId: VersionId,
      content: String,
      layoutData: LayoutData,
      color: String,
      dimensions: Dimensions,
      targetEdge: Option[String]
  )

  case class StickyNotesSettings(
      maxContentLength: Int,
      maxNotesCount: Int
  )

  sealed trait StickyNotesError

  implicit lazy val cellErrorSchema: Schema[LayoutData] = Schema.derived

  object StickyNotesError {

    final case class NoScenario(scenarioName: ProcessName) extends StickyNotesError
    final case object NoPermission                         extends StickyNotesError with CustomAuthorizationError
    final case class StickyNoteContentTooLong(count: Int, max: Int) extends StickyNotesError
    final case class StickyNoteCountLimitReached(max: Int)          extends StickyNotesError
    final case class NoStickyNote(noteId: StickyNoteId)             extends StickyNotesError

    implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoScenario](e => s"No scenario ${e.scenarioName} found")

    implicit val noStickyNoteCodec: Codec[String, NoStickyNote, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoStickyNote](e =>
        s"No sticky note with id: ${e.noteId} was found"
      )

    implicit val stickyNoteContentTooLongCodec: Codec[String, StickyNoteContentTooLong, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[StickyNoteContentTooLong](e =>
        s"Provided note content is too long (${e.count} characters). Max content length is ${e.max}."
      )

    implicit val stickyNoteCountLimitReachedCodec: Codec[String, StickyNoteCountLimitReached, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[StickyNoteCountLimitReached](e =>
        s"Cannot add another sticky note, since max number of sticky notes was reached: ${e.max} (see configuration)."
      )

  }

}
