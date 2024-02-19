package pl.touk.nussknacker.engine.management.sample.dict

import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition

/**
  * Dictionary which is use at integration tests
  */
object TestDictionary {
  val id: String = "dict"

  val definition: EmbeddedDictDefinition = EmbeddedDictDefinition(
    Map(
      "foo"                           -> "Foo",
      "bar"                           -> "Bar",
      "sentence-with-spaces-and-dots" -> "Sentence with spaces and . dots",
      "_some_key_"                    -> "Pretty label"
    )
  )

  val instance: DictInstance = DictInstance(id, definition)
}
