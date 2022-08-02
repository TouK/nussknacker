package pl.touk.nussknacker.engine.api.generics

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

trait ExpressionParseError {
  def message: String
}

class ArgumentTypeError(val found: Signature, val possibleSignatures: List[Signature]) extends ExpressionParseError {
  override def message: String =
    s"Mismatch parameter types. Found: ${found.display}. Required: ${possibleSignatures.map(_.display).mkString(" or ")}"
}

class GenericFunctionError(messageInner: String) extends ExpressionParseError {
  override def message: String = messageInner
}


final class Signature(val name: String, val noVarArgs: List[TypingResult], val varArg: Option[TypingResult]) {
  private def typesToString(types: List[TypingResult]): String =
    types.map(_.display).mkString(", ")

  def display: String = varArg match {
    case Some(x) => s"$name(${typesToString(noVarArgs :+ x)}...)"
    case None => s"$name(${typesToString(noVarArgs)})"
  }

  override def equals(obj: Any): Boolean = obj match {
    case x: Signature => name == x.name && noVarArgs == x.noVarArgs && varArg == x.varArg
    case _ => false
  }
}

