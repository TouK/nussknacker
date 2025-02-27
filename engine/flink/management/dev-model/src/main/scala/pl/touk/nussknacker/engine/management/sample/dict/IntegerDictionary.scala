package pl.touk.nussknacker.engine.management.sample.dict

import pl.touk.nussknacker.engine.api.dict.{DictDefinition, DictInstance, ReturningKeyWithoutTransformation}
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed

object IntegerDictionary {
  val id: String = "integer_dict"

  val definition: DictDefinition = new EmbeddedDictDefinition with ReturningKeyWithoutTransformation {
    override def valueType(dictId: String): typing.SingleTypingResult = Typed.typedClass[java.lang.Integer]

    override def labelByKey: Map[String, String] = Map(
      "-2147483648" -> "large (negative) number",
      "42"          -> "small number",
      "2147483647"  -> "big number"
    )

  }

  val instance: DictInstance = DictInstance(id, definition)
}
