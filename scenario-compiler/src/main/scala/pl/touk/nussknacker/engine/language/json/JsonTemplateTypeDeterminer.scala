package pl.touk.nussknacker.engine.language.json

import cats.data.Validated.validNel
import cats.data.ValidatedNel
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.springframework.expression.{Expression => SpringExpression}
import org.springframework.expression.common.{CompositeStringExpression, LiteralExpression}
import org.springframework.expression.spel.standard
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.json.decoders.FromJsonSimpleDecoder
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.component.parameter.defaults.TypeValueDeterminer
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.language.json.JsonTemplateTypeDeterminer._
import pl.touk.nussknacker.engine.spel.{SpelExpression, SpelExpressionParser, SpelExpressionRepr}

private[json] class JsonTemplateTypeDeterminer(spelParser: SpelExpressionParser) extends LazyLogging {

  def expressionResultType(
      spelTemplateExpression: CompiledExpression,
      validationContext: ValidationContext
  ): ValidatedNel[ExpressionParseError, TypingResult] = {
    // We convert spel template to json with placholders, because it is a convenient representation for typing.
    // Another option could be preparation of our own AST similar to Json AST
    withSpelExpression(spelTemplateExpression)(toValidJsonWithPlaceholders(_, validationContext))
      // FIXME abr: it will generate wrong error positions
      .map { jsonWithPlaceholders =>
        logger.debug(
          s"Expression: ${spelTemplateExpression.original} was transformed to json with placeholders: $jsonWithPlaceholders"
        )
        jsonWithPlaceholders
      }
      .andThen(JsonParser.parseWithoutContextValidation(_, Unknown))
      .map(jsonExpression => computeType(jsonExpression.json))
  }

  private def withSpelExpression[T](
      compiledExpression: CompiledExpression
  )(handle: SpringExpression => T): T =
    compiledExpression match {
      case expression: SpelExpression =>
        handle(expression.parsedSpringExpression)
      case _ =>
        throw new IllegalStateException(
          s"Invalid compiled expression: ${compiledExpression.getClass.getName}. Expected: ${classOf[SpelExpression].getName}"
        )
    }

  private def toValidJsonWithPlaceholders(
      expression: SpringExpression,
      validationContext: ValidationContext
  ): ValidatedNel[ExpressionParseError, String] =
    expression match {
      case expression: CompositeStringExpression =>
        expression.getExpressions.toList
          .map(toValidJsonWithPlaceholders(_, validationContext))
          .sequence
          .map(_.mkString)
      case expression: LiteralExpression => validNel(expression.getValue)
      case expression: standard.SpelExpression =>
        toPlaceholder(UnparsedSpelExpression(expression.getExpressionString), validationContext)
      case _ =>
        throw new IllegalStateException(
          s"Unknown expression type: ${expression.getClass.getName}"
        )
    }

  private def computeType(validJsonWithPlaceholders: Json) = {
    val obj = FromJsonSimpleDecoder.jsonToAny(validJsonWithPlaceholders)
    Typed.fromInstance(obj).withoutValue
  }

  private def toPlaceholder(
      expression: UnparsedSpelExpression,
      validationContext: ValidationContext
  ): ValidatedNel[ExpressionParseError, String] =
    spelParser
      .parse(expression.value, validationContext, defaultSpelTypingResult)
      .map(_.typingInfo.typingResult)
      .map(toPlaceholder)

  private def toPlaceholder(typingResult: TypingResult): String = typingResult match {
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

private object JsonTemplateTypeDeterminer {

  val defaultSpelTypingResult: TypingResult     = Typed[SpelExpressionRepr]
  val placeHolderForIntegerNumber: String       = "0"
  val placeHolderForFloatingPointNumber: String = "0.5"
  val placeHolderForBoolean: String             = "true"
  val placeHolderForString: String              = "unquoted string"
  val specialMarkerForUnknownTypes: String      = """{"$nu$":1}"""

  final case class UnparsedSpelExpression(value: String) extends AnyVal

}
