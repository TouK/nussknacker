package pl.touk.nussknacker.engine.spel

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.Valid
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.expression.IndexBasedTextRange
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.{Flavour, Template}
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingError.{
  ExpressionTypeError,
  SpelExpressionTypingErrorWithTextRange
}
import pl.touk.nussknacker.engine.spel.parser.ExpressionWithTextRange

class SpelExpressionValidator(typer: Typer) {

  def validate(
      expr: ExpressionWithTextRange,
      ctx: ValidationContext,
      expectedType: TypingResult,
      flavour: Flavour
  ): Validated[NonEmptyList[SpelExpressionTypingErrorWithTextRange], CollectedTypingResult] = {
    val typedExpression = typer.typeExpression(expr, ctx)
    typedExpression.andThen { collected =>
      if (expectedType == Typed[SpelExpressionRepr]) {
        Valid(collected)
      } else if (flavour == Template) {
        // We don't validate the result type for templates, because we eventually run toString on the result
        Valid(collected.withFinalTypingResult(Typed[String]))
      } else {
        validateResultMatchExpectedType(collected.finalResult.typingResult, expectedType, expr.getTextRange).map(_ =>
          collected
        )
      }
    }
  }

  private def validateResultMatchExpectedType(
      resultType: TypingResult,
      expectedType: TypingResult,
      textRange: IndexBasedTextRange,
  ) = {
    if (resultType.canBeLooselyAssignedTo(expectedType))
      ().valid
    else
      SpelExpressionTypingErrorWithTextRange(ExpressionTypeError(expectedType, resultType), textRange).invalidNel
  }

  def withTyper(modify: Typer => Typer): SpelExpressionValidator =
    new SpelExpressionValidator(modify(typer))

}
