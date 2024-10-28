package pl.touk.nussknacker.ui.api.description.stickynotes

object StickyNoteEvent extends Enumeration {
  type StickyNoteEvent = Value
  val StickyNoteCreated, StickyNoteUpdated, StickyNoteDeleted = Value
}
