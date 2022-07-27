package pl.touk.nussknacker.engine.api.generics

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

trait SpelParseError {
  def message: String
}

class ArgumentTypeError(found: Signature, possibleSignatures: List[Signature]) extends SpelParseError {
  override def message: String =
    s"Mismatch parameter types. Found: ${found.display}. Required: ${possibleSignatures.map(_.display).mkString(" or ")}"
}

class GenericFunctionError(messageInner: String) extends SpelParseError {
  override def message: String = messageInner
}


sealed abstract class Signature {
  def display: String

  protected def typesToString(types: List[TypingResult]): String =
    types.map(_.display).mkString(", ")
}

class NoVarArgSignature(name: String, types: List[TypingResult]) extends Signature {
  def display = s"$name(${typesToString(types)})"
}

class VarArgSignature(name: String, noVarArgs: List[TypingResult], varArg: TypingResult) extends Signature {
  def display = s"$name(${typesToString(noVarArgs :+ varArg)}...)"
}

