package pl.touk.nussknacker.engine.expression.parse

import cats.data.{Validated, ValidatedNel}
import cats.data.Validated.Valid
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.{TypedNull, TypingResult}
import pl.touk.nussknacker.engine.expression.NullExpression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

trait ExpressionParser {

  def languageId: Language

  def parse(
      original: String,
      ctx: ValidationContext,
      expectedType: TypingResult
  ): ValidatedNel[ExpressionParseError, TypedExpression]

  protected def handleBlankExpressionAsNullExpression(
      expressionString: String
  ): Option[ValidatedNel[ExpressionParseError, TypedExpression]] = {
    if (expressionString.isBlank) {
      Some(
        Valid(
          TypedExpression(
            new NullExpression(expressionString, languageId),
            ExpressionTypingInfo(TypedNull)
          )
        )
      )
    } else {
      None
    }
  }

}

object ExpressionParser {

  def validateResultTypeMatchExpectedType(
      resultType: TypingResult,
      expectedType: TypingResult
  ): ValidatedNel[ExpressionParseError, Unit] = {
    Validated.condNel(
      resultType.canBeLooselyAssignedTo(expectedType),
      (),
      BadTypeExpressionParseError(expectedType, resultType)
    )
  }

  private[engine] case class BadTypeExpressionParseError(expected: TypingResult, found: TypingResult)
      extends ExpressionParseError {
    override def message: String = s"Bad expression type, expected: ${expected.display}, found: ${found.display}"
  }

}
