package pl.touk.nussknacker.engine.spel

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import org.springframework.expression.Expression
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.generics.SpelParseError
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.ExpressionTypeError

class SpelExpressionValidator(typer: Typer) {

  def validate(expr: Expression, ctx: ValidationContext, expectedType: TypingResult): Validated[NonEmptyList[SpelParseError], CollectedTypingResult] = {
    val typedExpression = typer.typeExpression(expr, ctx)
    typedExpression.andThen { collected =>
      collected.finalResult.typingResult match {
        case typ: TypingResult if typ.canBeSubclassOf(expectedType) || expectedType == Typed[SpelExpressionRepr] => Valid(collected)
        case typ: TypingResult => Invalid(NonEmptyList.of(ExpressionTypeError(expectedType, typ)))
      }
    }
  }

  def withTyper(modify: Typer => Typer): SpelExpressionValidator =
    new SpelExpressionValidator(modify(typer))

}
