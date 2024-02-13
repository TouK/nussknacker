package pl.touk.nussknacker.engine.spel.parser

import cats.data.Validated.{Valid, invalidNel}
import cats.data.{NonEmptyList, Validated}
import io.circe.parser
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.{AdditionalVariable => _}
import pl.touk.nussknacker.engine.api.expression.{Expression => CompiledExpression, _}
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.LabelWithKeyJsonParsingError
import pl.touk.nussknacker.engine.spel.parser.LiteralExpressionParser.LiteralExpression

object LabelWithKeyExpressionParser extends ExpressionParser {

  override def languageId: String = Expression.Language.LabelWithKey

  override def parse(
      labelWithKey: String,
      ctx: ValidationContext,
      expectedType: typing.TypingResult
  ): Validated[NonEmptyList[ExpressionParseError], TypedExpression] =
    parseWithoutContextValidation(labelWithKey, expectedType).map(
      TypedExpression(_, Typed[String], LiteralExpressionTypingInfo(typing.Unknown))
    )

  override def parseWithoutContextValidation(
      labelWithKey: String,
      expectedType: TypingResult
  ): Validated[NonEmptyList[ExpressionParseError], CompiledExpression] = {
    extractKey(labelWithKey).map(LiteralExpression)
  }

  def extractKey(labelWithKey: String): Validated[NonEmptyList[LabelWithKeyJsonParsingError], String] =
    parser.parse(labelWithKey) match {
      case Left(e) => invalidNel(LabelWithKeyJsonParsingError(labelWithKey, e.message))
      case Right(json) =>
        json.hcursor.downField("key").as[String] match {
          case Right(key) => Valid(key)
          case Left(_) =>
            invalidNel(
              LabelWithKeyJsonParsingError(
                labelWithKey,
                s"LabelWithKey expression json doesn't contain key: $labelWithKey"
              )
            )
        }
    }

}
