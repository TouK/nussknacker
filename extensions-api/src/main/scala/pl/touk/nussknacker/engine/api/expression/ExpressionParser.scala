package pl.touk.nussknacker.engine.api.expression

import cats.data.ValidatedNel
import org.springframework.expression.spel.ast.MethodReference
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

trait ExpressionParser {

  def languageId: String

  def parse(original: String, ctx: ValidationContext, expectedType: TypingResult):
  ValidatedNel[TypingError, TypedExpression]

  def parseWithoutContextValidation(original: String, expectedType: TypingResult): ValidatedNel[TypingError, Expression]

}

trait TypingError {
  def message: String
}

object TypingError {
  case class ArgumentNumberError(expected: Int, found: Int) extends TypingError {
    override def message: String = s"Illegal number of arguments: expected $expected, found $found"
  }

  case class VarArgumentNumberError(expected: Int, found: Int) extends TypingError {
    override def message: String = s"Illegal number of arguments: expected at least $expected, found $found"
  }

  case class ArgumentTypeError(expected: TypingResult, found: TypingResult, argName: String) extends TypingError {
    override def message: String = s"Invalid type of argument $argName: expected ${expected.display}, found ${found.display}"
  }

  object InvocationOnUnknown extends TypingError {
    override def message: String = s"Method invocation on Unknown is not allowed"
  }

  case class UnknownMethod(name: String, displayableType: String) extends TypingError {
    override def message: String = s"No method named $name in type $displayableType"
  }

  case class InvalidMethodReference(reference: MethodReference) extends TypingError {
    override def message: String = s"Invalid method reference ${reference.toStringAST}"
  }

  case class ExpressionParseError(message: String) extends TypingError
}
