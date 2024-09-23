package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.Documentation
import pl.touk.nussknacker.engine.api.generics.GenericFunctionTypingError
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectWithValue}

import scala.util.Try

@SkipAutoDiscovery
sealed trait Cast {

  @Documentation(description = "Checks if a type can be casted to a given class")
  def canCastTo(className: String): Boolean

  @Documentation(description = "Casts a type to a given class or throws exception if type cannot be casted.")
  def castTo[T](className: String): T

}

object CastTyping {

  def castToTyping(allowedClasses: AllowedClasses)(
      instanceType: typing.TypingResult,
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = arguments match {
    case TypedObjectWithValue(_, clazzName: String) :: Nil =>
      allowedClasses.get(clazzName) match {
        case Some(typing) => typing.validNel
        case None         => GenericFunctionTypingError.OtherError(s"$clazzName is not allowed").invalidNel
      }
    case _ => GenericFunctionTypingError.ArgumentTypeError.invalidNel
  }

  def canCastToTyping(allowedClasses: AllowedClasses)(
      instanceType: typing.TypingResult,
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] =
    castToTyping(allowedClasses)(instanceType, arguments).map(_ => Typed.typedClass[Boolean])

}

class CastImpl(target: Any, classLoader: ClassLoader) extends Cast {

  override def canCastTo(className: String): Boolean =
    classLoader.loadClass(className).isAssignableFrom(target.getClass)

  override def castTo[T](className: String): T = Try {
    val clazz = classLoader.loadClass(className)
    if (clazz.isInstance(target)) {
      clazz.cast(target).asInstanceOf[T]
    } else {
      throw new ClassCastException(s"Cannot cast: ${target.getClass} to: $className")
    }
  }.get

}
