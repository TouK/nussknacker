package pl.touk.nussknacker.engine.types

import cats.data.{Validated, ValidatedNel}
import cats.implicits.{catsSyntaxValidatedId, toTraverseOps}
import pl.touk.nussknacker.engine.api.generics.ParameterList
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

object ParameterListSubclassChecker {
  def check(subclassParameters: ParameterList, superclassParameters: ParameterList): ValidatedNel[ParameterListError, Unit] = {
    val ParameterList(subclassNoVarArg, subclassVarArgOption) = subclassParameters
    val ParameterList(superclassNoVarArg, superclassVarArgOption) = superclassParameters

    val validatedVarArgs = (subclassVarArgOption, superclassVarArgOption) match {
      case (Some(sub), Some(sup)) if sub.refClazz.canBeSubclassOf(sup.refClazz) => ().validNel
      case (Some(sub), Some(sup)) => NotSubclassVarArgument(sub.refClazz, sup.refClazz).invalidNel
      case (Some(_), None) => BadVarArg.invalidNel
      case (None, Some(_)) => ().validNel
      case (None, None) => ().validNel
    }

    val validatedLength = if (superclassVarArgOption.isDefined)
      Validated.condNel(subclassNoVarArg.length >= superclassNoVarArg.length, (), NotEnoughArguments(subclassNoVarArg.length, superclassNoVarArg.length))
    else
      Validated.condNel(subclassNoVarArg.length == superclassNoVarArg.length, (), WrongNumberOfArguments(subclassNoVarArg.length, superclassNoVarArg.length))

    val zippedParameters = subclassNoVarArg.zip(superclassVarArgOption.map(superclassNoVarArg.padTo(subclassNoVarArg.length, _)).getOrElse(superclassNoVarArg))
    val validatedNoVarArgs = zippedParameters.zipWithIndex.map {
      case ((sub, sup), _) if sub.refClazz.canBeSubclassOf(sup.refClazz) => ().validNel
      case ((sub, sup), i) => NotSubclassArgument(i + 1, sub.refClazz, sup.refClazz).invalidNel
    }.sequence.map(_ => ())

    validatedVarArgs combine validatedLength combine validatedNoVarArgs
  }
}

trait ParameterListError {
  def message: String
}

case object BadVarArg extends ParameterListError {
  override def message: String =
    s"function with varargs cannot be more specific than function without varargs"
}

case class NotEnoughArguments(found: Int, expected: Int) extends ParameterListError {
  override def message: String =
    s"not enough no-vararg arguments: found $found, expected $expected"
}

case class WrongNumberOfArguments(found: Int, expected: Int) extends ParameterListError {
  override def message: String =
    s"wrong number of no-vararg arguments: found $found, expected: $expected"
}

case class NotSubclassArgument(position: Int, found: TypingResult, expected: TypingResult) extends ParameterListError {
  override def message: String =
    s"argument at position $position has illegal type: ${found.display} cannot be subclass of ${expected.display}"
}

case class NotSubclassVarArgument(found: TypingResult, expected: TypingResult) extends ParameterListError {
  override def message: String =
    s"vararg argument has illegal type: ${found.display} cannot be subclass of ${expected.display}"
}