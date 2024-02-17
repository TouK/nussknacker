package pl.touk.nussknacker.engine.management.sample.dict

import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition

object BooleanDictionary {
  val id: String = "boolean_dict"

  val definition: EmbeddedDictDefinition = EmbeddedDictDefinition(
    Map(
      "true"  -> "ON",
      "false" -> "OFF"
    )
  )

  val instance: DictInstance = DictInstance(id, definition)
}
