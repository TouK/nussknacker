package pl.touk.nussknacker.engine.api.generics

import cats.Eq
import cats.kernel.Eq.neqv
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

trait ExpressionParseError {
  def message: String
}

class ArgumentTypeError(val found: NoVarArgSignature, val possibleSignatures: List[Signature]) extends ExpressionParseError {
  override def message: String =
    s"Mismatch parameter types. Found: ${found.display}. Required: ${possibleSignatures.map(_.display).mkString(" or ")}"

  def combine(x: ArgumentTypeError): ArgumentTypeError = {
    if (!found.equals(x.found)) throw new IllegalArgumentException("Cannot combine ArgumentTypeErrors where found signatures differ.")
    new ArgumentTypeError(found, possibleSignatures ::: x.possibleSignatures)
  }
}

class GenericFunctionError(messageInner: String) extends ExpressionParseError {
  override def message: String = messageInner
}


sealed abstract class Signature {
  def display: String

  protected def typesToString(types: List[TypingResult]): String =
    types.map(_.display).mkString(", ")
}

class NoVarArgSignature(val name: String, val types: List[TypingResult]) extends Signature {
  def display = s"$name(${typesToString(types)})"

  override def equals(obj: Any): Boolean = obj match {
    case x: NoVarArgSignature => name == x.name && types == x.types
    case _ => false
  }
}

class VarArgSignature(val name: String, val noVarArgs: List[TypingResult], val varArg: TypingResult) extends Signature {
  def display = s"$name(${typesToString(noVarArgs :+ varArg)}...)"
}

