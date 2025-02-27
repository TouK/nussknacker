package pl.touk.nussknacker.engine.lite

import cats.~>
import cats.data.{Validated, ValidatedNel}
import cats.data.Validated.Valid
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CannotCreateObjectError
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.CapabilityTransformer

import scala.language.higherKinds
import scala.reflect.runtime.universe.TypeTag

object capabilities {

  // We are not using here TypeTag.equals since type prefix (tpe.pre) there is computed like this: targetTag.tpe == tag.tpe
  // It is a problem when FixedCapabilityTransformer[T] and T type are defined in the same object
  // In such a case in FixedCapabilityTransformer prefix of type T in TypeTag[T] is defined as UniqueThisType
  // but implicitly added tag: TypeTag[F] in transform method has prefix of type F defined as UniqueSingleType (despite that it is same type)
  def equalTags(targetTag: TypeTag[_], tag: TypeTag[_]): Boolean = {
    targetTag.mirror == tag.mirror && targetTag.tpe =:= tag.tpe
  }

  class FixedCapabilityTransformer[Target[_]](implicit targetTag: TypeTag[Target[Any]])
      extends CapabilityTransformer[Target] {

    override def transform[From[_]](
        implicit tag: TypeTag[From[Any]]
    ): ValidatedNel[ProcessCompilationError, From ~> Target] = {
      if (equalTags(targetTag, tag)) {
        Valid(new ~>[From, Target] {
          override def apply[A](fa: From[A]): Target[A] = fa.asInstanceOf[Target[A]]
        })
      } else {
        Validated.invalidNel(CannotCreateObjectError(s"Cannot convert capability: $tag", ""))
      }
    }

  }

}
