package pl.touk.nussknacker.ui.api.description.stickynotes

import io.circe.generic.JsonCodec

@JsonCodec case class StickyNotesSettings(
    maxContentLength: Int,
    maxNotesCount: Option[Int] = None,
    enabled: Boolean
)

object StickyNotesSettings {
  val default: StickyNotesSettings = StickyNotesSettings(maxContentLength = 5000, enabled = true)
}
