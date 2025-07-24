package pl.touk.nussknacker.engine.language.json

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{validNel, Invalid, Valid}
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
import pl.touk.nussknacker.engine.language.json.JsonParser.CompiledJsonExpression
import pl.touk.nussknacker.engine.language.json.JsonTemplateTypeDeterminer._
import pl.touk.nussknacker.engine.spel.{SpelExpression, SpelExpressionParser, SpelExpressionRepr}
import pl.touk.nussknacker.engine.util.Implicits._

import java.math.{BigDecimal => JBigDecimal}
import java.nio.charset.Charset
import java.time.{
  Duration,
  Instant,
  LocalDate,
  LocalDateTime,
  LocalTime,
  OffsetDateTime,
  Period,
  ZonedDateTime,
  ZoneId,
  ZoneOffset
}
import java.util.{Currency, Locale, UUID}
import scala.collection.immutable.ListMap

private[json] class JsonTemplateTypeDeterminer(spelParser: SpelExpressionParser) extends LazyLogging {

  def expressionResultType(
      spelTemplateExpression: CompiledExpression,
      validationContext: ValidationContext
  ): ValidatedNel[ExpressionParseError, TypingResult] = {
    // We convert spel template to json with placholders, because it is a convenient representation for typing.
    // Another option could be preparation of our own AST mixing Json AST and SpEL AST
    withSpelExpression(spelTemplateExpression) { spelExpression =>
      toValidJsonWithPlaceholdersFilled(spelExpression, validationContext)
        .andThen { jsonWithPlaceholdersFilled =>
          logger.debug(
            s"Expression [${spelTemplateExpression.original}] was transformed to json with placeholders filled [$jsonWithPlaceholdersFilled] for the expression typing purpose"
          )
          // This step may generate a wrong error position because we fill placeholders but it not a problem because we abandon validation result
          JsonParser
            .parse(jsonWithPlaceholdersFilled, validationContext, Unknown)
            .fold(
              handleJsonWithPlaceholdersFilledParseError(spelExpression, _),
              typedJsonExpression =>
                Valid(
                  computeTypeForJsonWithPlaceholdersFilled(
                    typedJsonExpression.expression.asInstanceOf[CompiledJsonExpression].json
                  )
                )
            )
        }
    }
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

  private def toValidJsonWithPlaceholdersFilled(
      expression: SpringExpression,
      validationContext: ValidationContext
  ): ValidatedNel[ExpressionParseError, String] =
    expression match {
      case expression: CompositeStringExpression =>
        expression.getExpressions.toList
          .map(toValidJsonWithPlaceholdersFilled(_, validationContext))
          .sequence
          .map(_.mkString)
      case expression: LiteralExpression => validNel(expression.getValue)
      case expression: standard.SpelExpression =>
        toValuePlacedInPlaceholder(UnparsedSpelExpression(expression.getExpressionString), validationContext)
      case _ =>
        throw new IllegalStateException(
          s"Unknown expression type: ${expression.getClass.getName}"
        )
    }

  private def toValuePlacedInPlaceholder(
      expression: UnparsedSpelExpression,
      validationContext: ValidationContext
  ): ValidatedNel[ExpressionParseError, String] =
    spelParser
      .parse(expression.value, validationContext, defaultSpelTypingResult)
      .map(_.typingInfo.typingResult)
      .map(_.toValuePlacedInPlaceholder)
      .map { json =>
        // String expressions may be used in multiple contexts. See CompiledJsonTemplateExpression.renderExpressionResult
        // For UC 1 and UC 3 it is better to use unquoted string, for UC 2, a user has to wrap expression with #CONV.toJsonString()
        json.asString
          .getOrElse(json.noSpaces)
      }

  private def handleJsonWithPlaceholdersFilledParseError(
      spelExpression: SpringExpression,
      errors: NonEmptyList[ExpressionParseError]
  ) = {
    def abandonErrors() = {
      logger.debug(
        s"Found validation errors [${errors.toList.mkString(", ")}] during json with placeholders validation. " +
          s"We can't be sure that they are real errors, so we be ignore them and return ${Typed.json} type instead"
      )
      Valid(Typed.json)
    }

    spelExpression match {
      // No placeholder filled - we can return errors
      case _: LiteralExpression => Invalid(errors)
      // When any expression was used, we have to be loose, json-structure-based types are only a hint for further validation.
      // In general, we should allow every templating logic
      case _: CompositeStringExpression =>
        abandonErrors()
      case _: standard.SpelExpression =>
        abandonErrors()
      case _ =>
        throw new IllegalStateException(
          s"Unknown expression type: ${spelExpression.getClass.getName}"
        )
    }
  }

  private def computeTypeForJsonWithPlaceholdersFilled(validJsonWithPlaceholdersFilled: Json) = {
    val obj = FromJsonSimpleDecoder.jsonToAny(validJsonWithPlaceholdersFilled)
    JsonTemplateFromInstanceTypeDeterminer.fromInstance(obj)
  }

}

private object JsonTemplateTypeDeterminer {

  private val defaultSpelTypingResult: TypingResult          = Typed[SpelExpressionRepr]
  private val placeholderValueForIntegerNumber: Int          = 0
  private val placeholderValueForFloatingPointNumber: Double = 0.1
  private val placeholderValueForBoolean: Boolean            = false

  // String expressions may be used in two contexts: either for string value or for templating logic
  // See CompiledJsonTemplateExpression.renderExpressionResult UC 1 and UC 3
  // We can't use blank string in this place, because for the second case (UC 3), we want to produce broken json to avoid
  // invalid type returning. It is better to return unknown json type than invalid type
  private val placeholderValueForStringAndTemplatingLogic: String = "unquoted string"

  // We use some arbitrary chosen ranom numeric, because it is a valid value in most places:
  // - in unquoted values
  // - inside quoted values (we don't need to escape some quotes)
  // This value will be typed as an unknown json
  private val specialMarkerForUnknownTypes = JBigDecimal.valueOf(0.6568369117280021)

  private val specialMarkersForLogicalTypes = List[(JBigDecimal, TypedClass)](
    JBigDecimal.valueOf(0.6568369117280022) -> Typed.typedClass[Instant],
    JBigDecimal.valueOf(0.6568369117280023) -> Typed.typedClass[OffsetDateTime],
    JBigDecimal.valueOf(0.6568369117280024) -> Typed.typedClass[ZonedDateTime],
    JBigDecimal.valueOf(0.6568369117280025) -> Typed.typedClass[LocalDateTime],
    JBigDecimal.valueOf(0.6568369117280026) -> Typed.typedClass[LocalDate],
    JBigDecimal.valueOf(0.6568369117280027) -> Typed.typedClass[LocalTime],
    JBigDecimal.valueOf(0.6568369117280028) -> Typed.typedClass[Duration],
    JBigDecimal.valueOf(0.6568369117280031) -> Typed.typedClass[Period],
    JBigDecimal.valueOf(0.6568369117280032) -> Typed.typedClass[ZoneOffset],
    JBigDecimal.valueOf(0.6568369117280033) -> Typed.typedClass[ZoneId],
    JBigDecimal.valueOf(0.6568369117280034) -> Typed.typedClass[Currency],
    JBigDecimal.valueOf(0.6568369117280035) -> Typed.typedClass[Locale],
    JBigDecimal.valueOf(0.6568369117280036) -> Typed.typedClass[UUID],
    JBigDecimal.valueOf(0.6568369117280037) -> Typed.typedClass[Charset],
  ).map { case (key, value) => key.toString -> value } toMapCheckingDuplicatesUnsafe

  private object TypeMarkedUsingSpecialMarker {
    private val specialMarkersForLogicalTypesSwapped =
      specialMarkersForLogicalTypes.toList.map(_.swap).toMapCheckingDuplicatesUnsafe

    def unapply(typ: TypedClass): Option[String] = specialMarkersForLogicalTypesSwapped.get(typ)
  }

  private final case class UnparsedSpelExpression(value: String) extends AnyVal

  private object JsonTemplateFromInstanceTypeDeterminer extends FromInstanceTypeDeterminer {

    override protected val highPriorityTypeDeterminer: PartialFunction[Any, TypingResult] =
      specialMarkersForLogicalTypes.toMap[Any, TypingResult] orElse { case `specialMarkerForUnknownTypes` =>
        Typed.json
      }

    override def fromInstance(obj: Any): TypingResult =
      super
        .fromInstance(obj)
        .withoutValue // We have to remove values because they can be values placed in placeholders
        .unknownToJson

  }

  implicit class TypingResultExt(typ: TypingResult) {

    def toValuePlacedInPlaceholder: Json = typ.withoutValue match {
      // lists
      case TypedClass(clazz, param :: Nil) if TypeValueDeterminer.isList(clazz) =>
        Json.fromValues(List(param.toValuePlacedInPlaceholder))
      // maps
      case TypedObjectTypingResult(fields, _, _) =>
        Json.fromFields(fields.toList.map { case (fieldName, fieldValue) =>
          fieldName -> fieldValue.toValuePlacedInPlaceholder
        })
      // primitive types
      case TypedClass(clazz, _) if TypeValueDeterminer.isIntegerNumber(clazz) =>
        Json.fromInt(placeholderValueForIntegerNumber)
      case TypedClass(clazz, _) if TypeValueDeterminer.isFloatingPointNumber(clazz) =>
        Json.fromDoubleOrNull(placeholderValueForFloatingPointNumber)
      case TypedClass(clazz, _) if TypeValueDeterminer.isBoolean(clazz) => Json.fromBoolean(placeholderValueForBoolean)
      // strings and templating logic
      case TypedClass(clazz, _) if TypeValueDeterminer.isString(clazz) =>
        Json.fromString(placeholderValueForStringAndTemplatingLogic)
      // logical types
      case TypeMarkedUsingSpecialMarker(specialMarkerValue) =>
        Json.fromString(specialMarkerValue)
      // For now, for more complex types we use a number, because we don't want to break the validation and numbers are acceptable in most places
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
