package pl.touk.nussknacker.engine.language.json

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.{invalidNel, Valid}
import io.circe.{parser, Json, ParsingFailure}
import org.apache.commons.lang3.StringUtils
import org.typelevel.jawn.ParseException
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.{
  CoordinatesBasedTextRange,
  ErrorDetails,
  TextCoordinates
}
import pl.touk.nussknacker.engine.api.json.decoders.FromJsonSimpleDecoder
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, ExpressionParser, TypedExpression}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

object JsonParser extends ExpressionParser {

  private val messageWithCoordinatesRegex = " \\(line \\d+, column \\d+\\)".r

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

  private def parseJson(jsonString: String): Validated[NonEmptyList[JsonParseError], Json] =
    if (shouldBeTreatedAsNull(jsonString)) {
      Valid(Json.Null)
    } else {
      parser.parse(jsonString) match {
        case Left(ParsingFailure(message, underlying: ParseException)) =>
          val messageWithoutCoordinates = messageWithCoordinatesRegex.replaceFirstIn(message, "")
          invalidNel(
            JsonParseError(
              messageWithoutCoordinates,
              Some(
                CoordinatesBasedTextRange(
                  TextCoordinates(underlying.col - 1, underlying.line - 1),
                  TextCoordinates(underlying.col, underlying.line - 1)
                )
              )
            )
          )
        case Left(ParsingFailure(message, _)) => invalidNel(JsonParseError(message, None))
        case Right(json)                      => Valid(json)
      }
    }

  private def shouldBeTreatedAsNull(jsonString: String) = StringUtils.isBlank(jsonString)

  case class CompiledJsonExpression(originalJsonString: String, json: Json) extends CompiledExpression {

    override def language: Language = languageId

    override def original: String = originalJsonString

    override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = json.asInstanceOf[T]
  }

  case class JsonParseError(message: String, override val details: Option[CoordinatesBasedTextRange])
      extends ExpressionParseError

}

case class JsonExpressionTypingInfo(json: Json) extends ExpressionTypingInfo {

  override val typingResult: TypingResult = {
    Typed.fromInstance(FromJsonSimpleDecoder.jsonToAnyWithOrderKeeping(json))
  }

}
