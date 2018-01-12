package pl.touk.nussknacker.engine.spel

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import org.springframework.expression.Expression
import org.springframework.expression.spel.standard
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.compile.ValidationContext
import pl.touk.nussknacker.engine.compiledgraph.expression.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

class SpelExpressionValidator(implicit classLoader: ClassLoader) {

  private val typer = new Typer()(classLoader)

  def validate(expr: Expression, ctx: ValidationContext, expectedType: ClazzRef): Validated[NonEmptyList[ExpressionParseError], TypingResult] = {
    Validated.fromOption(Option(expr.asInstanceOf[standard.SpelExpression].getAST), NonEmptyList.of(ExpressionParseError("Empty expression")))
      .andThen { ast =>
       typer.typeExpression(ctx, ast).andThen {
        case a: TypingResult if a.canBeSubclassOf(expectedType) => Valid(a)
        case a: TypingResult => Invalid(NonEmptyList.of(ExpressionParseError(s"Bad expression type, expected: ${expectedType.refClazzName}, found: ${a.display}")))
      }
    }
  }

}
