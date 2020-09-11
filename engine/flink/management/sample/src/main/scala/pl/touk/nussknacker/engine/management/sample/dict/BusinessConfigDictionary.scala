package pl.touk.nussknacker.engine.management.sample.dict

import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition

/**
  * Example dict which shows how we can use dictionary with BC
  */
object BusinessConfigDictionary {
  val id: String = "bc"

  val definition: EmbeddedDictDefinition = EmbeddedDictDefinition(Map(
    "6b9469d2-6afe-41eb-ba59-9295e01374e4" -> "Marketing v1",
    "c9bf7d92-e238-4910-be64-21bea90a1fdd" -> "Nussknacker base configuration ",
    "9d6d4e3e-0ba6-43bb-8696-58432e8f6bd8" -> "Campaign 2020 News",
    "b10d27bc-fc36-49d2-a60d-1001c7dd37c2" -> "Email Marketing 12.2019",
    "1fb79cec-f958-452a-b5c7-f3695ffd71fd" -> "IT Special BC",
    "1e6bfed3-2249-4f96-9426-379c006c8275" -> "Users Black List"
  ))

  val instance: DictInstance = DictInstance(id, definition)
}
