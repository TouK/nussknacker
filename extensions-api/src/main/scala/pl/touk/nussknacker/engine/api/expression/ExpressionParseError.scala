package pl.touk.nussknacker.engine.api.expression

import org.springframework.expression.spel.ast.MethodReference
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

trait ExpressionParseError {
  def message: String
}

object ExpressionParseError {
  case class ArgumentNumberError(expected: Int, found: Int) extends ExpressionParseError {
    override def message: String = s"Illegal number of arguments: expected $expected, found $found"
  }

  case class VarArgumentNumberError(expected: Int, found: Int) extends ExpressionParseError {
    override def message: String = s"Illegal number of arguments: expected at least $expected, found $found"
  }

  case class ArgumentTypeError(expected: TypingResult, found: TypingResult, argName: String) extends ExpressionParseError {
    override def message: String = s"Invalid type of argument $argName: expected ${expected.display}, found ${found.display}"
  }

  object InvocationOnUnknown extends ExpressionParseError {
    override def message: String = s"Method invocation on Unknown is not allowed"
  }

  case class UnknownMethod(name: String, displayableType: String) extends ExpressionParseError {
    override def message: String = s"No method named $name in type $displayableType"
  }

  case class InvalidMethodReference(reference: MethodReference) extends ExpressionParseError {
    override def message: String = s"Invalid method reference ${reference.toStringAST}"
  }

  case class OtherError(message: String) extends ExpressionParseError
}
