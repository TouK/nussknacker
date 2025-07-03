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
import pl.touk.nussknacker.engine.api.typed.FromInstanceTypeDeterminer
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.definition.component.parameter.defaults.TypeValueDeterminer
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.language.json.JsonTemplateTypeDeterminer._
import pl.touk.nussknacker.engine.spel.{SpelExpression, SpelExpressionParser, SpelExpressionRepr}

import scala.collection.immutable.ListMap

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

  private def toPlaceholder(
      expression: UnparsedSpelExpression,
      validationContext: ValidationContext
  ): ValidatedNel[ExpressionParseError, String] =
    spelParser
      .parse(expression.value, validationContext, defaultSpelTypingResult)
      .map(_.typingInfo.typingResult)
      .map(_.toPlaceholder)
      // String json is placed by user inside quotes so we unquote it. For other types we print json
      .map(json => json.asString.getOrElse(json.noSpaces))

  private def computeType(validJsonWithPlaceholders: Json) = {
    val obj = FromJsonSimpleDecoder.jsonToAny(validJsonWithPlaceholders)
    JsonTemplateFromInstanceTypeDeterminer.fromInstance(obj)
  }

}

private object JsonTemplateTypeDeterminer {

  private val defaultSpelTypingResult: TypingResult     = Typed[SpelExpressionRepr]
  private val placeHolderForIntegerNumber: Int          = 0
  private val placeHolderForFloatingPointNumber: Double = 0.1
  private val placeHolderForBoolean: Boolean            = false
  private val placeHolderForString: String              = ""

  // We use some arbitrary chosen ranom numeric, because it is a valid value in most places:
  // - in unquoted values
  // - inside quoted values (we don't need to escape some quotes)
  // This value will be typed as an unknown json
  private val specialMarkerForUnknownTypes: java.math.BigDecimal = java.math.BigDecimal.valueOf(0.6568369117280025)

  private final case class UnparsedSpelExpression(value: String) extends AnyVal

  private object JsonTemplateFromInstanceTypeDeterminer extends FromInstanceTypeDeterminer {

    override protected val highPriorityTypeDeterminer: PartialFunction[Any, TypingResult] = {
      case `specialMarkerForUnknownTypes` => Typed.json
    }

    override def fromInstance(obj: Any): TypingResult = super.fromInstance(obj).withoutValue.unknownToJson

  }

  implicit class TypingResultExt(typ: TypingResult) {

    def toPlaceholder: Json = typ.withoutValue match {
      // list
      case TypedClass(clazz, param :: Nil) if TypeValueDeterminer.isList(clazz) =>
        Json.fromValues(List(param.toPlaceholder))
      // map
      case TypedObjectTypingResult(fields, _, _) =>
        Json.fromFields(fields.toList.map { case (fieldName, fieldValue) =>
          fieldName -> fieldValue.toPlaceholder
        })
      // primitive types
      case TypedClass(clazz, _) if TypeValueDeterminer.isIntegerNumber(clazz) =>
        Json.fromInt(placeHolderForIntegerNumber)
      case TypedClass(clazz, _) if TypeValueDeterminer.isFloatingPointNumber(clazz) =>
        Json.fromDoubleOrNull(placeHolderForFloatingPointNumber)
      case TypedClass(clazz, _) if TypeValueDeterminer.isBoolean(clazz) => Json.fromBoolean(placeHolderForBoolean)
      case TypedClass(clazz, _) if TypeValueDeterminer.isString(clazz)  => Json.fromString(placeHolderForString)
      // For now, for more complex types we use a number, because we want to skip validation and the number is acceptable in most places
      case _ => Json.fromBigDecimal(specialMarkerForUnknownTypes)
    }

    def unknownToJson: TypingResult = typ match {
      case Unknown => Typed.json
      case obj @ TypedObjectTypingResult(fields, _, _) =>
        obj.copy(fields = ListMap(fields.toList.map { case (fieldName, fieldValue) =>
          fieldName -> fieldValue.unknownToJson
        }: _*))
      case clazz @ TypedClass(_, params) => clazz.copy(params = params.map(_.unknownToJson))
      case other                         => other
    }

  }

}
