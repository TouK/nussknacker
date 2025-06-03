package pl.touk.nussknacker.engine.spel

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.TemplateEvaluationResult
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingError.{
  ExpressionTypeError,
  SpelExpressionTypingErrorWithTextRange
}
import pl.touk.nussknacker.engine.spel.parser.ExpressionWithTextRange

class SpelExpressionValidator(typer: Typer) {

  def validate(
      expr: ExpressionWithTextRange,
      ctx: ValidationContext,
      expectedType: TypingResult
  ): Validated[NonEmptyList[SpelExpressionTypingErrorWithTextRange], CollectedTypingResult] = {
    val typedExpression = typer.typeExpression(expr, ctx)
    typedExpression.andThen { collected =>
      collected.finalResult.typingResult match {
        case _ if expectedType == Typed[SpelExpressionRepr] =>
          Valid(collected)
        case a if a == Typed[String] && expectedType == Typed[TemplateEvaluationResult] =>
          Valid(collected)
        case a if a.canBeLooselyAssignedTo(expectedType) =>
          Valid(collected)
        case a =>
          Invalid(
            NonEmptyList.of(
              SpelExpressionTypingErrorWithTextRange(ExpressionTypeError(expectedType, a), expr.getTextRange)
            )
          )
      }
    }
  }

  def withTyper(modify: Typer => Typer): SpelExpressionValidator =
    new SpelExpressionValidator(modify(typer))

}
