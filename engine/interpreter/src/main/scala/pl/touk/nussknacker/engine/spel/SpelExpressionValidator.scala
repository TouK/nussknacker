package pl.touk.nussknacker.engine.spel

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import org.springframework.expression.Expression
import org.springframework.expression.common.{CompositeStringExpression, LiteralExpression}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax
import cats.implicits._
import org.springframework.expression.spel.standard


class SpelExpressionValidator(implicit classLoader: ClassLoader) {

  private val typer = new Typer()(classLoader)

  def validate(expr: Expression, ctx: ValidationContext, expectedType: TypingResult): Validated[NonEmptyList[ExpressionParseError], TypingResult] = {
    val typedExpression = expr match {
      case e:standard.SpelExpression =>
        typeExpression(e, ctx)
      case e:CompositeStringExpression =>
        val validatedParts = e.getExpressions.toList.map(validate(_, ctx, Unknown)).sequence
        validatedParts.map(_ => Typed[String])
      case e:LiteralExpression =>
        Valid(Typed[String])
    }
    typedExpression.andThen {
      case a: TypingResult if a.canBeSubclassOf(expectedType) || expectedType == Typed[SpelExpressionRepr] => Valid(a)
      case a: TypingResult => Invalid(NonEmptyList.of(ExpressionParseError(s"Bad expression type, expected: ${expectedType.display}, found: ${a.display}")))
    }
  }

  private def typeExpression(spelExpression: standard.SpelExpression, ctx: ValidationContext): Validated[NonEmptyList[ExpressionParseError], TypingResult] = {
    Validated.fromOption(Option(spelExpression.getAST), NonEmptyList.of(ExpressionParseError("Empty expression")))
      .andThen(typer.typeExpression(ctx, _))
  }

}
