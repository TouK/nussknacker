package pl.touk.nussknacker.engine.management.sample.dict

import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition

/**
  * Example dict which presents how we can use dictionaries - RGB colors example
  */
object RGBDictionary {
  val id: String = "rgb"

  val definition: EmbeddedDictDefinition = EmbeddedDictDefinition(Map(
    "#000000" -> "Black",
    "#800000" -> "Maroon",
    "#008000" -> "Green",
    "#00ffff" -> "Aqua",
    "#0000ff" -> "Blue",
    "#5f0000" -> "DarkRed",
    "#d7af87" -> "Tan",
    "#ff0000" -> "Red",
    "#ff00ff" -> "Magenta",
    "#ffff00" -> "Yellow"
  ))

  val instance: DictInstance = DictInstance(id, definition)
}
