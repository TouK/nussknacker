package pl.touk.nussknacker.engine.management.sample.dict

import pl.touk.nussknacker.engine.api.dict.{DictDefinition, DictInstance, ReturningKeyWithoutTransformation}
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed

object LongDictionary {
  val id: String = "long_dict"

  val definition: DictDefinition = new EmbeddedDictDefinition with ReturningKeyWithoutTransformation {
    override def valueType(dictId: String): typing.SingleTypingResult = Typed.typedClass[java.lang.Long]

    override def labelByKey: Map[String, String] = Map(
      "-1500100900" -> "large (negative) number",
      "42"          -> "small number",
      "1234567890"  -> "big number"
    )

  }

  val instance: DictInstance = DictInstance(id, definition)
}
