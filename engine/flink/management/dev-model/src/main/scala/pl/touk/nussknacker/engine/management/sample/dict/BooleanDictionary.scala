package pl.touk.nussknacker.engine.management.sample.dict

import pl.touk.nussknacker.engine.api.dict.{DictDefinition, DictInstance, ReturningKeyWithoutTransformation}
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed

object BooleanDictionary {
  val id: String = "boolean_dict"

  val definition: DictDefinition = new EmbeddedDictDefinition with ReturningKeyWithoutTransformation {
    override def valueType(dictId: String): typing.SingleTypingResult = Typed.typedClass[java.lang.Boolean]

    override def labelByKey: Map[String, String] = Map(
      "true"  -> "ON",
      "false" -> "OFF"
    )

  }

  val instance: DictInstance = DictInstance(id, definition)
}
