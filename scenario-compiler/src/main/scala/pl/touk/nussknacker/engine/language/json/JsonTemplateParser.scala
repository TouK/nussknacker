package pl.touk.nussknacker.engine.language.json

import cats.data.Validated.{invalidNel, validNel}
import cats.data.ValidatedNel
import cats.implicits.toTraverseOps
import io.circe.parser
import org.springframework.expression.{Expression => SpringExpression}
import org.springframework.expression.common.{CompositeStringExpression, LiteralExpression}
import org.springframework.expression.spel.standard
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult}
import pl.touk.nussknacker.engine.definition.component.parameter.defaults.TypeValueDeterminer
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, ExpressionParser, TypedExpression}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.language.json.JsonTemplateParser.{
  stringTypingResult,
  CompiledJsonTemplateExpression,
  JsonTemplateExpressionTypingInfo
}
import pl.touk.nussknacker.engine.spel.{SpelExpression, SpelExpressionParser, SpelExpressionRepr}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.ExpressionCompilationError

class JsonTemplateParser(spelTemplateParser: SpelExpressionParser, spelParser: SpelExpressionParser)
    extends ExpressionParser {

  override def languageId: Expression.Language = Expression.Language.JsonTemplate

  override def parse(
      original: String,
      ctx: ValidationContext,
      expectedType: typing.TypingResult
  ): ValidatedNel[ExpressionParseError, TypedExpression] =
    spelTemplateParser
      .parse(original, ctx, stringTypingResult)
      .andThen(parsedSpelTemplateExpression =>
        extractJsonStringFromParsedExpression(parsedSpelTemplateExpression, ctx)
          .andThen(jsonString => JsonParser.parse(jsonString, ctx, expectedType))
          .map(_ => parsedSpelTemplateExpression)
      )
      .map(templateTypeExpression =>
        TypedExpression(
          CompiledJsonTemplateExpression(languageId, original, templateTypeExpression.expression, expectedType),
          JsonTemplateExpressionTypingInfo(templateTypeExpression.typingInfo),
        )
      )

  override def parseWithoutContextValidation(
      original: String,
      expectedType: typing.TypingResult
  ): ValidatedNel[ExpressionParseError, CompiledExpression] = spelTemplateParser
    .parseWithoutContextValidation(original, stringTypingResult)
    .andThen(parsedSpelTemplateExpression =>
      extractJsonStringFromCompiledExpression(parsedSpelTemplateExpression)
        .andThen(jsonString => JsonParser.parseWithoutContextValidation(jsonString, expectedType))
        .map(_ => parsedSpelTemplateExpression)
    )
    .map(templateTypeExpression =>
      CompiledJsonTemplateExpression(languageId, original, templateTypeExpression, expectedType)
    )

  private def extractJsonStringFromParsedExpression(
      typedExpression: TypedExpression,
      ctx: ValidationContext,
  ): ValidatedNel[ExpressionParseError, String] =
    typedExpression.expression match {
      case expression: SpelExpression =>
        joinLiteralsAndReplaceExpressionWithDefaults(
          spelExpressionToDefaultJsonValue(ctx),
          expression.parsedSpringExpression,
        )
      case _ => invalidNel(ExpressionCompilationError("Invalid compiled expression type"))
    }

  private def joinLiteralsAndReplaceExpressionWithDefaults(
      spelExpressionToDefaultJsonValue: (String, typing.TypingResult) => ValidatedNel[ExpressionParseError, String],
      expression: SpringExpression,
  ): ValidatedNel[ExpressionParseError, String] = expression match {
    case expression: CompositeStringExpression =>
      expression.getExpressions.toList
        .map(e => joinLiteralsAndReplaceExpressionWithDefaults(spelExpressionToDefaultJsonValue, e))
        .sequence
        .map(_.mkString)
    case expression: LiteralExpression => validNel(expression.getValue)
    case expression: standard.SpelExpression =>
      spelExpressionToDefaultJsonValue(expression.getExpressionString, Typed[SpelExpressionRepr])
    case _ => invalidNel(ExpressionCompilationError("Unknown expression type"))
  }

  private def spelExpressionToDefaultJsonValue(ctx: ValidationContext)(
      original: String,
      expectedType: typing.TypingResult
  ): ValidatedNel[ExpressionParseError, String] = spelParser
    .parse(original, ctx, expectedType)
    .map(_.typingInfo.typingResult)
    .map(typingResultToDefaultJsonValue)

  private def typingResultToDefaultJsonValue(typingResult: TypingResult): String = typingResult match {
    case TypedClass(k, _) =>
      k.getName match {
        case className if TypeValueDeterminer.isLikeIntegerNumber(className)       => "0"
        case className if TypeValueDeterminer.isLikeFloatingPointNumber(className) => "0.0"
        case className if TypeValueDeterminer.isBoolean(className)                 => "true"
        case className if TypeValueDeterminer.isString(className)                  => ""
        // For now, complex types are treated as String
        case _ => ""
      }
    // For now, complex types are treated as String
    case _ => ""
  }

  private def extractJsonStringFromCompiledExpression(
      compiledExpression: CompiledExpression
  ): ValidatedNel[ExpressionParseError, String] =
    compiledExpression match {
      case expression: SpelExpression =>
        joinLiteralsAndReplaceExpressionWithDefaults(
          spelExpressionToDefaultJsonValue,
          expression.parsedSpringExpression,
        )
      case _ => invalidNel(ExpressionCompilationError("Invalid compiled expression type"))
    }

  private def spelExpressionToDefaultJsonValue(
      original: String,
      expectedType: typing.TypingResult
  ): ValidatedNel[ExpressionParseError, String] = spelParser
    .parseWithoutContextValidation(original, expectedType)
    .map(_ => "0")

}

object JsonTemplateParser {
  private val stringTypingResult = Typed.typedClass[String]

  case class CompiledJsonTemplateExpression(
      languageId: Language,
      originalJsonString: String,
      templateCompiledExpression: CompiledExpression,
      expectedType: typing.TypingResult
  ) extends CompiledExpression {

    override def language: Language = languageId

    override def original: String = originalJsonString

    override def evaluate[T](ctx: Context, globals: Map[String, Any]): T =
      if (expectedType == stringTypingResult) {
        templateCompiledExpression.evaluate[T](ctx, globals)
      } else {
        val jsonString = templateCompiledExpression.evaluate[String](ctx, globals)
        // For now, it only return JSON, but Map, List and other types is albo possible
        parser.parse(jsonString) match {
          case Left(error)  => throw new IllegalStateException("Parsing JSON failed with error", error)
          case Right(value) => value.asInstanceOf[T]
        }
      }

  }

  case class JsonTemplateExpressionTypingInfo(typingInfo: ExpressionTypingInfo) extends ExpressionTypingInfo {

    override val typingResult: TypingResult = typingInfo.typingResult.withoutValue

  }

}
