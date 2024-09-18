package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.Documentation
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, GenericType, TypingFunction}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectWithValue}

import scala.util.{Failure, Success, Try}

sealed trait Cast {

  @Documentation(description = "Checks if a type can be casted to a given class")
  def canCastTo(clazzType: String): Boolean

  @Documentation(description = "Casts a type to a given class or throws exception if type cannot be casted.")
  @GenericType(typingFunction = classOf[CastTyping.Typing])
  def castTo[T](clazzType: String): T

}

object CastTyping {

  class Typing extends TypingFunction {

    override def computeResultType(
        arguments: List[typing.TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = arguments match {
      case TypedObjectWithValue(_, clazzName: String) :: Nil =>
        Try(Class.forName(clazzName)) match {
          case Success(clazz) => Typed.typedClass(clazz).validNel
          case Failure(_)     => GenericFunctionTypingError.ArgumentTypeError.invalidNel
        }
      case _ => GenericFunctionTypingError.ArgumentTypeError.invalidNel
    }

  }

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
