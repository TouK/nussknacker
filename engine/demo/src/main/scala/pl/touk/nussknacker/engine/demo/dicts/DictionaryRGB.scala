package pl.touk.nussknacker.engine.demo.dicts

import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition

object DictionaryRGB {
  val dictId: String = "rgb"

  val dictDefinition: EmbeddedDictDefinition = EmbeddedDictDefinition(Map(
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

  val dictInstance: DictInstance = DictInstance(dictId, dictDefinition)
}
