package pl.touk.nussknacker.engine.language.json

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.{invalidNel, Valid}
import io.circe.{parser, Json}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.json.decoders.FromJsonSimpleDecoder
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, ExpressionParser, TypedExpression}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.JsonParsingError

object JsonParser extends ExpressionParser {

  override def languageId: Language = Expression.Language.Json

  override def parse(
      jsonString: String,
      ctx: ValidationContext,
      expectedType: TypingResult
  ): ValidatedNel[ExpressionParseError, TypedExpression] = {
    parseJson(jsonString).map { json =>
      TypedExpression(
        CompiledJsonExpression(jsonString, json),
        JsonExpressionTypingInfo(json)
      )
    }
  }

  override def parseWithoutContextValidation(
      jsonString: String,
      expectedType: TypingResult
  ): ValidatedNel[ExpressionParseError, CompiledExpression] = {
    parseJson(jsonString).map(json => CompiledJsonExpression(jsonString, json))
  }

  private def parseJson(jsonString: String): Validated[NonEmptyList[JsonParsingError], Json] =
    parser.parse(jsonString) match {
      case Left(error) => invalidNel(JsonParsingError(error.message))
      case Right(json) => Valid(json)
    }

  case class CompiledJsonExpression(originalJsonString: String, json: Json) extends CompiledExpression {

    override def language: Language = languageId

    override def original: String = originalJsonString

    override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = json.asInstanceOf[T]
  }

}

case class JsonExpressionTypingInfo(json: Json) extends ExpressionTypingInfo {

  override val typingResult: TypingResult = {
    Typed.fromInstance(FromJsonSimpleDecoder.jsonToAny(json))
  }

}
