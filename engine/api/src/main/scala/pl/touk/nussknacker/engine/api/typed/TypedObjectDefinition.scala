package pl.touk.nussknacker.engine.api.typed

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

import scala.collection.immutable.ListMap

object TypedObjectDefinition {
  def apply(fields: List[(String, TypingResult)]): TypedObjectDefinition =
    TypedObjectDefinition(ListMap(fields: _*))
}

case class TypedObjectDefinition(fields: ListMap[String, TypingResult])
