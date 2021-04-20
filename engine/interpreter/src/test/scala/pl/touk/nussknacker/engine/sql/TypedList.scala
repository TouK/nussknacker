package pl.touk.nussknacker.engine.sql

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}

import scala.reflect.runtime.universe._

object TypedList {
  def apply[T: TypeTag]: TypingResult =
    Typed.fromDetailedType[List[T]]

  def apply(fields: List[(String, TypingResult)]): TypingResult =
    Typed.genericTypeClass[java.util.List[_]](List(TypedObjectTypingResult(fields)))

}
