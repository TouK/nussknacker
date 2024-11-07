package pl.touk.nussknacker.engine.spel

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import org.springframework.expression.Expression
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.ExpressionTypeError

class SpelExpressionValidator(typer: Typer) {

  def validate(
      expr: Expression,
      ctx: ValidationContext,
      expectedType: TypingResult
  ): Validated[NonEmptyList[ExpressionParseError], CollectedTypingResult] = {
    val typedExpression = typer.typeExpression(expr, ctx)
    typedExpression.andThen { collected =>
      collected.finalResult.typingResult match {
        case a: TypingResult
            if a.canBeImplicitlyConvertedTo(expectedType) || expectedType == Typed[SpelExpressionRepr] =>
          Valid(collected)
        case a: TypingResult =>
          Invalid(NonEmptyList.of(ExpressionTypeError(expectedType, a)))
      }
    }
  }

  def withTyper(modify: Typer => Typer): SpelExpressionValidator =
    new SpelExpressionValidator(modify(typer))

}
