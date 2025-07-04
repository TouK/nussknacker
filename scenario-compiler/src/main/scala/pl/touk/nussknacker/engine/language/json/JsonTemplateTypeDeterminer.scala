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
import pl.touk.nussknacker.engine.api.typed.typing._
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
    // Another option could be preparation of our own AST mixing Json AST and SpEL AST
    withSpelExpression(spelTemplateExpression)(toValidJsonWithPlaceholders(_, validationContext))
      .map { jsonWithPlaceholders =>
        logger.debug(
          s"Expression [${spelTemplateExpression.original}] was transformed to json with placeholders [$jsonWithPlaceholders] for the expression typing purpose"
        )
        jsonWithPlaceholders
      }
      // TODO: this step generates wrong error positions, because placeholders change the structure of the text
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

  private def toPlaceholder(
      expression: UnparsedSpelExpression,
      validationContext: ValidationContext
  ): ValidatedNel[ExpressionParseError, String] =
    spelParser
      .parse(expression.value, validationContext, defaultSpelTypingResult)
      .map(_.typingInfo.typingResult)
      .map(_.toPlaceholder)

  private def computeType(validJsonWithPlaceholders: Json) = {
    val obj = FromJsonSimpleDecoder.jsonToAny(validJsonWithPlaceholders)
    Typed.fromInstance(obj).withoutValue
  }

}

private object JsonTemplateTypeDeterminer {

  private val defaultSpelTypingResult: TypingResult     = Typed[SpelExpressionRepr]
  private val placeHolderForIntegerNumber: String       = "0"
  private val placeHolderForFloatingPointNumber: String = "0.5"
  private val placeHolderForBoolean: String             = "true"
  private val placeHolderForString: String              = "unquoted string"

  private final case class UnparsedSpelExpression(value: String) extends AnyVal

  implicit class TypingResultExt(typ: TypingResult) {

    def toPlaceholder: String = typ.withoutValue match {
      case TypedClass(clazz, _) if TypeValueDeterminer.isIntegerNumber(clazz) =>
        placeHolderForIntegerNumber
      case TypedClass(clazz, _) if TypeValueDeterminer.isFloatingPointNumber(clazz) =>
        placeHolderForFloatingPointNumber
      case TypedClass(clazz, _) if TypeValueDeterminer.isBoolean(clazz) => placeHolderForBoolean
      case TypedClass(clazz, _) if TypeValueDeterminer.isString(clazz)  => placeHolderForString
      // For now, complex types are treated as String. In runtime, .toString is invoked on these types.
      case _ => placeHolderForString
    }

  }

}
