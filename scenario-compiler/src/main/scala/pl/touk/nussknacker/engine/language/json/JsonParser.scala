package pl.touk.nussknacker.engine.language.json

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.{invalidNel, Valid}
import io.circe.{parser, Json}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.json.decoders.FromJsonTypingResultBasedDecoder
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, ExpressionParser, TypedExpression}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.language.json.JsonParsingFailureToExpressionParseErrorConverter.ParsingFailureExt

object JsonParser extends ExpressionParser {

  override def languageId: Language = Expression.Language.Json

  override def parse(
      jsonString: String,
      ctx: ValidationContext,
      expectedType: TypingResult
  ): ValidatedNel[ExpressionParseError, TypedExpression] =
    handleBlankExpressionAsNullExpression(jsonString).getOrElse {
      parseJson(jsonString)
        .andThen { json =>
          decodeJson(expectedType, json).map { decodedJson =>
            val typ = Typed.fromInstance(decodedJson)
            TypedExpression(
              CompiledJsonExpression(jsonString, json),
              ExpressionTypingInfo(typ)
            )
          }
        }
        .andThen { typedExpression =>
          ExpressionParser
            .validateResultTypeMatchExpectedType(typedExpression.typingInfo.typingResult, expectedType)
            .map(_ => typedExpression)
        }
    }

  private def parseJson(jsonString: String): Validated[NonEmptyList[ExpressionParseError], Json] =
    parser.parse(jsonString) match {
      case Left(parseFailure) =>
        invalidNel(parseFailure.toExpressionParseError)
      case Right(json) => Valid(json)
    }

  private def decodeJson(expectedType: TypingResult, json: Json) = {
    Validated
      .fromEither(
        FromJsonTypingResultBasedDecoder.decodeValue(expectedType, json.hcursor).left.map { err =>
          JsonDecodingError(err.getMessage())
        }
      )
      .toValidatedNel
  }

  private case class CompiledJsonExpression(originalJsonString: String, json: Json) extends CompiledExpression {

    override def language: Language = languageId

    override def original: String = originalJsonString

    override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = json.asInstanceOf[T]
  }

  private[json] case class JsonDecodingError(override val message: String) extends ExpressionParseError

}
