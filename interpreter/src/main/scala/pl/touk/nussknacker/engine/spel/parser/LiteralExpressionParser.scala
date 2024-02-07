package pl.touk.nussknacker.engine.spel.parser

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, Validated}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.{AdditionalVariable => _}
import pl.touk.nussknacker.engine.api.expression.{Expression => CompiledExpression, ExpressionParser, _}
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.graph.expression.Expression

case class LiteralExpressionTypingInfo(typingResult: TypingResult) extends ExpressionTypingInfo

object LiteralExpressionParser extends ExpressionParser {

  override def languageId: String = Expression.Language.Literal

  override def parse(
      original: String,
      ctx: ValidationContext,
      expectedType: typing.TypingResult
  ): Validated[NonEmptyList[ExpressionParseError], TypedExpression] =
    parseWithoutContextValidation(original, expectedType).map(
      TypedExpression(_, Typed[String], LiteralExpressionTypingInfo(typing.Unknown))
    )

  override def parseWithoutContextValidation(
      original: String,
      expectedType: TypingResult
  ): Validated[NonEmptyList[ExpressionParseError], CompiledExpression] = Valid(
    LiteralExpression(original)
  )

  case class LiteralExpression(original: String) extends CompiledExpression {
    override def language: String = languageId

    override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = original.asInstanceOf[T]
  }

}
