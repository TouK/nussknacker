package pl.touk.nussknacker.engine.api.expression

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

trait ExpressionParser {

  def languageId: String

  def parse(original: String, ctx: ValidationContext, expectedType: TypingResult):
  ValidatedNel[ExpressionParseError, TypedExpression]

  def parseWithoutContextValidation(original: String, expectedType: TypingResult): ValidatedNel[ExpressionParseError, Expression]

}

case class ExpressionParseError(message: String)
