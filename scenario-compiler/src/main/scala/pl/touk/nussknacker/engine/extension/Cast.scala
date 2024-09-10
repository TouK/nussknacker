package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, GenericType, TypingFunction}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectWithValue}

import scala.util.{Failure, Success, Try}

sealed trait Cast {

  def canCastTo(clazzType: String): Boolean

  @GenericType(typingFunction = classOf[Cast.CastToTyping])
  def castTo[T](clazzType: String): T
}

object Cast {

  private class CastToTyping extends TypingFunction {

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

  override def castTo[T](clazzType: String): T =
    target.asInstanceOf[T]
}

object CastImpl {

  def apply(target: Any): Cast =
    new CastImpl(target)
}
