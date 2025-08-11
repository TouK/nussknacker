package pl.touk.nussknacker.engine.expression.parse

import cats.data.Validated.Valid
import cats.data.ValidatedNel
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
