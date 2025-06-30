package pl.touk.nussknacker.engine.language.json

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.{validNel, Invalid, Valid}
import cats.implicits.toTraverseOps
import io.circe.parser
import org.springframework.expression.{Expression => SpringExpression}
import org.springframework.expression.common.{CompositeStringExpression, LiteralExpression}
import org.springframework.expression.spel.standard
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.json.decoders.FromJsonTypingResultBasedDecoder
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult}
import pl.touk.nussknacker.engine.definition.component.parameter.defaults.TypeValueDeterminer
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, ExpressionParser, TypedExpression}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.language.json.JsonTemplateParser._
import pl.touk.nussknacker.engine.language.json.JsonTemplateParser.SpelExpressionConverter._
import pl.touk.nussknacker.engine.spel.{SpelExpression, SpelExpressionParser, SpelExpressionRepr}

class JsonTemplateParser(spelTemplateParser: SpelExpressionParser, spelParser: SpelExpressionParser)
    extends ExpressionParser {
  private val withoutContextValidationSpelExpressionConverter =
    new WithoutContextValidationSpelExpressionConverter(spelParser)

  override def languageId: Expression.Language = Expression.Language.JsonTemplate

  override def parse(
      original: String,
      ctx: ValidationContext,
      expectedType: typing.TypingResult
  ): ValidatedNel[ExpressionParseError, TypedExpression] = {
    def validateExpectedType(parsed: TypedExpression) = {
      Validated.condNel(
        parsed.typingInfo.typingResult.canBeLooselyAssignedTo(expectedType),
        parsed,
        JsonTemplateExpressionTypeError(expectedType, parsed.typingInfo.typingResult)
      )
    }

    def convertSpelTemplateToJsonTemplate(spelTemplateExpression: TypedExpression) = {
      extractJsonStringFromParsedExpression(
        spelTemplateExpression.expression,
        new WithContextValidationSpelExpressionConverter(spelParser, ctx)
      ).andThen(JsonParser.parse(_, ctx, expectedType))
        .map { typedJsonExpression =>
          TypedExpression(
            new CompiledJsonTemplateExpression(
              original,
              spelTemplateExpression.expression,
              expectedType
            ),
            new JsonTemplateExpressionTypingInfo(typedJsonExpression.typingInfo),
          )
        }
    }

    spelTemplateParser
      .parse(original, ctx, stringTypingResult)
      .andThen(convertSpelTemplateToJsonTemplate)
      .andThen(validateExpectedType)
  }

  override def parseWithoutContextValidation(
      original: String,
      expectedType: typing.TypingResult
  ): ValidatedNel[ExpressionParseError, CompiledExpression] = spelTemplateParser
    .parseWithoutContextValidation(original, stringTypingResult)
    .andThen { parsedSpelTemplateExpression =>
      extractJsonStringFromParsedExpression(
        parsedSpelTemplateExpression,
        withoutContextValidationSpelExpressionConverter
      )
        .andThen { jsonString => JsonParser.parseWithoutContextValidation(jsonString, expectedType) }
        .map { _ => parsedSpelTemplateExpression }
    }
    .map { templateTypeExpression =>
      new CompiledJsonTemplateExpression(original, templateTypeExpression, expectedType)
    }

  private def extractJsonStringFromParsedExpression(
      compiledExpression: CompiledExpression,
      converter: SpelExpressionConverter,
  ): ValidatedNel[ExpressionParseError, String] =
    compiledExpression match {
      case expression: SpelExpression =>
        convertSpelExpressionToJsonWithDefaultValues(converter, expression.parsedSpringExpression)
      case _ =>
        throw new IllegalStateException(
          s"Invalid compiled expression: ${compiledExpression.getClass.getName}. Expected: ${classOf[SpelExpression].getName}"
        )
    }

  private def convertSpelExpressionToJsonWithDefaultValues(
      converter: SpelExpressionConverter,
      expression: SpringExpression,
  ): ValidatedNel[ExpressionParseError, String] = expression match {
    case expression: CompositeStringExpression =>
      expression.getExpressions.toList
        .map(e => convertSpelExpressionToJsonWithDefaultValues(converter, e))
        .sequence
        .map(_.mkString)
    case expression: LiteralExpression => validNel(expression.getValue)
    case expression: standard.SpelExpression =>
      converter.toJsonDefaultValue(UnparsedSpelExpression(expression.getExpressionString))
    case _ =>
      throw new IllegalStateException(
        s"Unknown expression type: ${expression.getClass.getName}"
      )
  }

}

object JsonTemplateParser {
  private val stringTypingResult = Typed.typedClass[String]

  private class CompiledJsonTemplateExpression(
      originalJsonString: String,
      templateCompiledExpression: CompiledExpression,
      expectedType: typing.TypingResult
  ) extends CompiledExpression {

    override def language: Language = Expression.Language.JsonTemplate

    override def original: String = originalJsonString

    override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = {
      val jsonString = templateCompiledExpression.evaluate[String](ctx, globals)
      parser
        .parse(jsonString)
        .flatMap { value =>
          FromJsonTypingResultBasedDecoder.decodeValue(expectedType, value.hcursor)
        }
        .fold(e => throw new JsonTemplateEvaluationException(originalJsonString, e), _.asInstanceOf[T])
    }

  }

  private class JsonTemplateExpressionTypingInfo(typingInfo: ExpressionTypingInfo) extends ExpressionTypingInfo {
    override val typingResult: TypingResult = typingInfo.typingResult.withoutValue
  }

  private class JsonTemplateEvaluationException(
      input: String,
      cause: Throwable,
  ) extends NonTransientException(
        input = input,
        message = s"Expression [$input] evaluation failed, message: ${cause.getMessage}",
        cause = cause
      )

  final case class UnparsedSpelExpression(value: String) extends AnyVal

  trait SpelExpressionConverter {
    def toJsonDefaultValue(expression: UnparsedSpelExpression): ValidatedNel[ExpressionParseError, String]
  }

  object SpelExpressionConverter {
    val defaultSpelTypingResult: TypingResult     = Typed[SpelExpressionRepr]
    val placeHolderForIntegerNumber: String       = "0"
    val placeHolderForFloatingPointNumber: String = "0.5"
    val placeHolderForBoolean: String             = "true"
    val placeHolderForString: String              = "unquoted string"
    val specialMarkerForUnknownTypes: String      = """{"$nu$":1}"""
  }

  class WithContextValidationSpelExpressionConverter(spelParser: SpelExpressionParser, context: ValidationContext)
      extends SpelExpressionConverter {

    override def toJsonDefaultValue(expression: UnparsedSpelExpression): ValidatedNel[ExpressionParseError, String] =
      spelParser
        .parse(expression.value, context, defaultSpelTypingResult)
        .map(_.typingInfo.typingResult)
        .map(typingResultToDefaultJsonValue)

    private def typingResultToDefaultJsonValue(typingResult: TypingResult): String = typingResult match {
      case TypedClass(k, _) =>
        k match {
          case clazz if TypeValueDeterminer.isIntegerNumber(clazz)       => placeHolderForIntegerNumber
          case clazz if TypeValueDeterminer.isFloatingPointNumber(clazz) => placeHolderForFloatingPointNumber
          case clazz if TypeValueDeterminer.isBoolean(clazz)             => placeHolderForBoolean
          case clazz if TypeValueDeterminer.isString(clazz)              => placeHolderForString
          // We have to mark unknown types with some special marker to type them correctly in the next stage
          case _ => specialMarkerForUnknownTypes
        }
      // We have to mark unknown types with some special marker to type them correctly in the next stage
      case _ => specialMarkerForUnknownTypes
    }

  }

  private class WithoutContextValidationSpelExpressionConverter(spelParser: SpelExpressionParser)
      extends SpelExpressionConverter {

    override def toJsonDefaultValue(expression: UnparsedSpelExpression): ValidatedNel[ExpressionParseError, String] =
      spelParser
        .parseWithoutContextValidation(expression.value, SpelExpressionConverter.defaultSpelTypingResult)
        .map(_ => placeHolderForIntegerNumber)

  }

  private case class JsonTemplateExpressionTypeError(
      expected: TypingResult,
      found: TypingResult
  ) extends ExpressionParseError {
    override def message: String = s"Bad expression type, expected: ${expected.display}, found: ${found.display}"
  }

}
