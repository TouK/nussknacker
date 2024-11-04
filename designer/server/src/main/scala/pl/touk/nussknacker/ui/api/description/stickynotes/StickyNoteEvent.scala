package pl.touk.nussknacker.ui.api.description.stickynotes

import io.circe.{Decoder, Encoder}

object StickyNoteEvent extends Enumeration {
  implicit val typeEncoder: Encoder[StickyNoteEvent.Value] = Encoder.encodeEnumeration(StickyNoteEvent)
  implicit val typeDecoder: Decoder[StickyNoteEvent.Value] = Decoder.decodeEnumeration(StickyNoteEvent)

  type StickyNoteEvent = Value
  val StickyNoteCreated: Value = Value("CREATED")
  val StickyNoteUpdated: Value = Value("UPDATED")
  val StickyNoteDeleted: Value = Value("DELETED")

}
