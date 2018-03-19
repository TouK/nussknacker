package pl.touk.nussknacker.engine.sql

import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.Typed

import scala.reflect.ClassTag

object TypedList{
  def apply[T](implicit classTag: ClassTag[T]): Typed=
    Typed(ClazzRef(classOf[List[T]], List(ClazzRef[T]))).asInstanceOf[Typed]
}
