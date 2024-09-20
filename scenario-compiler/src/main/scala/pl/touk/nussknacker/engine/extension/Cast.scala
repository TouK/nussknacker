package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.Documentation
import pl.touk.nussknacker.engine.api.generics.GenericFunctionTypingError
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectWithValue, TypingResult}

import scala.util.Try

@SkipAutoDiscovery
sealed trait Cast {

  @Documentation(description = "Checks if a type can be casted to a given class")
  def canCastTo(clazzType: String): Boolean

  @Documentation(description = "Casts a type to a given class or throws exception if type cannot be casted.")
  def castTo[T](clazzType: String): T

}

object CastTyping {

  def castToTyping(allowedClassNamesWithTyping: Map[String, TypingResult])(
      instanceType: typing.TypingResult,
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = arguments match {
    case TypedObjectWithValue(_, clazzName: String) :: Nil =>
      allowedClassNamesWithTyping.get(clazzName) match {
        case Some(typing) => typing.validNel
        case None         => GenericFunctionTypingError.OtherError(s"$clazzName is not allowed").invalidNel
      }
    case _ => GenericFunctionTypingError.ArgumentTypeError.invalidNel
  }

  def canCastToTyping(allowedClassNamesWithTyping: Map[String, TypingResult])(
      instanceType: typing.TypingResult,
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] =
    castToTyping(allowedClassNamesWithTyping)(instanceType, arguments).map(_ => Typed.typedClass[Boolean])

}

class CastImpl(target: Any) extends Cast {

  override def canCastTo(clazzType: String): Boolean =
    Class.forName(clazzType).isAssignableFrom(target.getClass)

  override def castTo[T](clazzType: String): T = Try {
    val clazz = Class.forName(clazzType)
    if (clazz.isInstance(target)) {
      clazz.cast(target).asInstanceOf[T]
    } else {
      throw new ClassCastException(s"Cannot cast: ${target.getClass} to: $clazzType")
    }
  }.get

}

object CastImpl {

  def apply(target: Any): Cast =
    new CastImpl(target)
}
