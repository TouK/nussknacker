package pl.touk.nussknacker.engine.language.json

import cats.data.Validated.{invalidNel, validNel}
import cats.data.ValidatedNel
import cats.implicits.toTraverseOps
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
          CompiledJsonTemplateExpression(languageId, original, templateTypeExpression),
          JsonTemplateExpressionTypingInfo(templateTypeExpression.typingInfo),
        )
      )

  override def parseWithoutContextValidation(
      original: String,
      expectedType: typing.TypingResult
  ): ValidatedNel[ExpressionParseError, CompiledExpression] = ???

  private def extractJsonStringFromParsedExpression(
      typedExpression: TypedExpression,
      ctx: ValidationContext,
  ): ValidatedNel[ExpressionParseError, String] =
    typedExpression.expression match {
      case expression: SpelExpression => joinLiteralsAndReplaceExpressionWithDefaults(expression.parsed.parsed, ctx)
      case _                          => invalidNel(ExpressionCompilationError("Invalid compiled expression type"))
    }

  private def joinLiteralsAndReplaceExpressionWithDefaults(
      expression: SpringExpression,
      ctx: ValidationContext
  ): ValidatedNel[ExpressionParseError, String] = expression match {
    case expression: CompositeStringExpression =>
      expression.getExpressions.toList
        .map(e => joinLiteralsAndReplaceExpressionWithDefaults(e, ctx))
        .sequence
        .map(_.mkString)
    case expression: LiteralExpression => validNel(expression.getValue)
    case expression: standard.SpelExpression =>
      spelParser
        .parse(expression.getExpressionString, ctx, Typed[SpelExpressionRepr])
        .andThen(_.typingInfo.typingResult match {
          case TypedClass(k, _) =>
            k.getName match {
              case className if TypeValueDeterminer.isLikeIntegerNumber(className)       => validNel("0")
              case className if TypeValueDeterminer.isLikeFloatingPointNumber(className) => validNel("0.0")
              case className if TypeValueDeterminer.isBoolean(className)                 => validNel("true")
              case className if TypeValueDeterminer.isString(className)                  => validNel("")
              // For now, complex types are treated as String
              case _ => validNel("")
            }
          // For now, complex types are treated as String
          case _ => validNel("")
        })
    case _ => invalidNel(ExpressionCompilationError("Unknown expression type"))
  }

}

object JsonTemplateParser {
  private val stringTypingResult = Typed.typedClass[String]

  case class CompiledJsonTemplateExpression(
      languageId: Language,
      originalJsonString: String,
      templateTypedExpression: TypedExpression
  ) extends CompiledExpression {

    override def language: Language = languageId

    override def original: String = originalJsonString

    // For now only string is supported as evaluation result, but it's also possible to return Map or JSON type.
    override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = templateTypedExpression.expression
      .evaluate[T](ctx, globals)
  }

  case class JsonTemplateExpressionTypingInfo(typingInfo: ExpressionTypingInfo) extends ExpressionTypingInfo {

    override val typingResult: TypingResult = typingInfo.typingResult.withoutValue

  }

}
