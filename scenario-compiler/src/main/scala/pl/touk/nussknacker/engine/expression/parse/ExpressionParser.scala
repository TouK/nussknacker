package pl.touk.nussknacker.engine.expression.parse

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.{AssignabilityDeterminer, ConversionNecessaryForTypeAssignment}
import pl.touk.nussknacker.engine.api.typed.ConversionStrategy.{Loose, Strict}
import pl.touk.nussknacker.engine.api.typed.typing.{TypedNull, TypingResult}
import pl.touk.nussknacker.engine.expression.{ExpectedType, NullExpression}
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

trait ExpressionParser {

  def languageId: Language

  def parse(
      original: String,
      ctx: ValidationContext,
      expectedType: ExpectedType
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
      expectedType: ExpectedType
  ): ValidatedNel[ExpressionParseError, ConversionNecessaryForTypeAssignment] = {
    val targetType = expectedType.typ
    val result = if (expectedType.strictTypeMatch) {
      AssignabilityDeterminer
        .isAssignable(resultType, targetType)(Strict)
    } else {
      AssignabilityDeterminer
        .isAssignable(resultType, targetType)(Loose)
    }

    result.leftMap { _ =>
      BadTypeExpressionParseError(targetType, resultType)
    }.toValidatedNel
  }

  private[engine] case class BadTypeExpressionParseError(expected: TypingResult, found: TypingResult)
      extends ExpressionParseError {
    override def message: String = s"Bad expression type, expected: ${expected.display}, found: ${found.display}"
  }

}
