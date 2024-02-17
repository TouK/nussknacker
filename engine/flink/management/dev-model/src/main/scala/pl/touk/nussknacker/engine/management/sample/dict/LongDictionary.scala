package pl.touk.nussknacker.engine.management.sample.dict

import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition

object LongDictionary {
  val id: String = "long_dict"

  val definition: EmbeddedDictDefinition = EmbeddedDictDefinition(
    Map(
      "-1500100900" -> "large (negative) number",
      "42"          -> "small number",
      "1234567890"  -> "big number"
    )
  )

  val instance: DictInstance = DictInstance(id, definition)
}
