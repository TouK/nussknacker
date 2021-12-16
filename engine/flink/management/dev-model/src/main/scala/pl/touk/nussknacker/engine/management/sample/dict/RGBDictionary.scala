package pl.touk.nussknacker.engine.management.sample.dict

import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition

/**
  * Example dict which presents how we can use dictionaries - RGB colors example
  * NOTE: we write H00ffff instead of #00ffff, as #00ffff is not proper SpEL identifier, and this case (dict key not being proper identifier) is not handle ATM.
  */
object RGBDictionary {
  val id: String = "rgb"

  val definition: EmbeddedDictDefinition = EmbeddedDictDefinition(Map(
    "H000000" -> "Black",
    "H800000" -> "Maroon",
    "H008000" -> "Green",
    "H00ffff" -> "Aqua",
    "H0000ff" -> "Blue",
    "H5f0000" -> "DarkRed",
    "Hd7af87" -> "Tan",
    "Hff0000" -> "Red",
    "Hff00ff" -> "Magenta",
    "Hffff00" -> "Yellow"
  ))

  val instance: DictInstance = DictInstance(id, definition)
}
