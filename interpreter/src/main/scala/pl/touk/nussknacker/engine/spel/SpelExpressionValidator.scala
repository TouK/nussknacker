package pl.touk.nussknacker.engine.spel

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import org.springframework.expression.Expression
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

class SpelExpressionValidator(typer: Typer) {

  def validate(expr: Expression, ctx: ValidationContext, expectedType: TypingResult): Validated[NonEmptyList[ExpressionParseError], CollectedTypingResult] = {
    val typedExpression = typer.typeExpression(expr, ctx)
    typedExpression.andThen { collected =>
      collected.finalResult.typingResult match {
        case a: TypingResult if a.canBeSubclassOf(expectedType) || expectedType == Typed[SpelExpressionRepr] => Valid(collected)
        case a: TypingResult => Invalid(NonEmptyList.of(ExpressionParseError(s"Bad expression type, expected: ${expectedType.display}, found: ${a.display}")))
      }
    }
  }

  def withTyper(modify: Typer => Typer): SpelExpressionValidator =
    new SpelExpressionValidator(modify(typer))

}
