package pl.touk.nussknacker.engine.baseengine

import cats.data.Validated.Valid
import cats.data.{Validated, ValidatedNel}
import cats.~>
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CannotCreateObjectError
import pl.touk.nussknacker.engine.baseengine.api.customComponentTypes.CapabilityTransformer

import scala.language.higherKinds
import scala.reflect.runtime.universe.TypeTag

object capabilities {

  class FixedCapabilityTransformer[Target[_]](implicit targetTag: TypeTag[Target[Any]]) extends CapabilityTransformer[Target] {
    override def transform[From[_]](implicit tag: TypeTag[From[Any]]): ValidatedNel[ProcessCompilationError, From ~> Target] = {
      if (targetTag == tag) {
        Valid(new ~>[From, Target] {
          override def apply[A](fa: From[A]): Target[A] = fa.asInstanceOf[Target[A]]
        })
      } else {
        Validated.invalidNel(new CannotCreateObjectError(s"Cannot convert capability: $tag", ""))
      }
    }
  }


}
