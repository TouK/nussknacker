package pl.touk.nussknacker.engine.language.json

import cats.data.Validated.{validNel, Invalid, Valid}
import cats.data.ValidatedNel
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import io.circe.{parser, HCursor, Json, ParsingFailure}
import io.circe.Decoder.Result
import org.springframework.expression.{Expression => SpringExpression}
import org.springframework.expression.common.{CompositeStringExpression, LiteralExpression}
import org.springframework.expression.spel.standard
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.json.decoders.{FromJsonSimpleDecoder, FromJsonTypingResultBasedDecoder}
import pl.touk.nussknacker.engine.api.typed.{FromInstanceTypeDeterminer, StandardTypesClasses}
import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses._
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.expression.ExpectedType
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.language.json.JsonParsingFailureToExpressionParseErrorConverter.ParsingFailureExt
import pl.touk.nussknacker.engine.language.json.JsonTemplateTypeDeterminer._
import pl.touk.nussknacker.engine.language.json.UnknownTypeToJsonConverter.UnknownTypeToJsonConverterOps
import pl.touk.nussknacker.engine.spel.{CompiledSpelExpression, SpelExpressionParser, SpelExpressionRepr}
import pl.touk.nussknacker.engine.util.Implicits._

import java.math.{BigDecimal => JBigDecimal}

private[json] class JsonTemplateTypeDeterminer(spelParser: SpelExpressionParser) extends LazyLogging {

  def expressionResultType(
      spelTemplateExpression: CompiledExpression,
      validationContext: ValidationContext,
      expectedType: ExpectedType
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
          parser.parse(jsonWithPlaceholdersFilled) match {
            case Left(err) =>
              handleJsonWithPlaceholdersFilledParseError(spelExpression, err)
            case Right(json) =>
              Valid(computeTypeForJsonWithPlaceholdersFilled(json, expectedType))
          }
        }
    }
  }

  private def withSpelExpression[T](
      compiledExpression: CompiledExpression
  )(handle: SpringExpression => T): T =
    compiledExpression match {
      case expression: CompiledSpelExpression =>
        handle(expression.parsedSpringExpression)
      case _ =>
        throw new IllegalStateException(
          s"Invalid compiled expression: ${compiledExpression.getClass.getName}. Expected: ${classOf[CompiledSpelExpression].getName}"
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
      .parse(expression.value, validationContext, ExpectedType.loose(defaultSpelTypingResult))
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
      error: ParsingFailure
  ) = {
    def abandonErrors() = {
      logger.debug(
        s"Found paring error [${error.getMessage()}] during json with placeholders validation. " +
          s"We can't be sure that they are real errors, so we be ignore them and return ${Typed.json} type instead"
      )
      Valid(Typed.json)
    }

    spelExpression match {
      // No placeholder filled - we can return errors
      case _: LiteralExpression => Invalid(error.toExpressionParseError).toValidatedNel
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

  private def computeTypeForJsonWithPlaceholdersFilled(
      validJsonWithPlaceholdersFilled: Json,
      expectedType: ExpectedType
  ) = {
    val obj = JsonTypingResultBasedDecoderWithFallbackToSimple.decodeWithFallback(
      expectedType.typ,
      validJsonWithPlaceholdersFilled.hcursor
    )
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

  private val specialMarkersForLogicalTypes = List[(JBigDecimal, Class[_])](
    JBigDecimal.valueOf(0.6568369117280022) -> StandardTypesClasses.InstantClass,
    JBigDecimal.valueOf(0.6568369117280023) -> StandardTypesClasses.OffsetDateTimeClass,
    JBigDecimal.valueOf(0.6568369117280024) -> StandardTypesClasses.ZonedDateTimeClass,
    JBigDecimal.valueOf(0.6568369117280025) -> StandardTypesClasses.LocalDateTimeClass,
    JBigDecimal.valueOf(0.6568369117280026) -> StandardTypesClasses.LocalDateClass,
    JBigDecimal.valueOf(0.6568369117280027) -> StandardTypesClasses.LocalTimeClass,
    JBigDecimal.valueOf(0.6568369117280028) -> StandardTypesClasses.DurationClass,
    JBigDecimal.valueOf(0.6568369117280031) -> StandardTypesClasses.PeriodClass,
    JBigDecimal.valueOf(0.6568369117280032) -> StandardTypesClasses.ZoneOffsetClass,
    JBigDecimal.valueOf(0.6568369117280033) -> StandardTypesClasses.ZoneIdClass,
    JBigDecimal.valueOf(0.6568369117280034) -> StandardTypesClasses.CurrencyClass,
    JBigDecimal.valueOf(0.6568369117280035) -> StandardTypesClasses.LocaleClass,
    JBigDecimal.valueOf(0.6568369117280036) -> StandardTypesClasses.UUIDClass,
    JBigDecimal.valueOf(0.6568369117280037) -> StandardTypesClasses.CharsetClass,
  ).map { case (key, value) => key.toString -> Typed.typedClass(value) }.toMapCheckingDuplicatesUnsafe

  private object TypeMarkedUsingSpecialMarker {
    private val specialMarkersForLogicalTypesSwapped =
      specialMarkersForLogicalTypes.toList.map(_.swap).toMapCheckingDuplicatesUnsafe

    def unapply(typ: TypedClass): Option[String] = specialMarkersForLogicalTypesSwapped.get(typ)
  }

  private final case class UnparsedSpelExpression(value: String) extends AnyVal

  // It is better to use FromJsonTypingResultBasedDecoder than just FromJsonSimpleDecoder because it
  // produces a correct logical types for literal values.
  // However, we have to do fallback to simple type because values placed in placeholders don't match given type
  private object JsonTypingResultBasedDecoderWithFallbackToSimple extends FromJsonTypingResultBasedDecoder {

    def decodeWithFallback(typ: TypingResult, cursor: HCursor): Any = {
      decodeValue(typ, cursor)
        .fold(err => throw new IllegalStateException("Fail-safe decoder returned error", err), identity)
    }

    override def decodeValue(typ: TypingResult, cursor: HCursor): Result[Any] =
      Right(super.decodeValue(typ, cursor).getOrElse(FromJsonSimpleDecoder.jsonToAny(cursor.value)))
  }

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
      case TypedClass(`ListClass` | `ArrayClass`, param :: Nil) =>
        Json.fromValues(List(param.toValuePlacedInPlaceholder))
      // maps
      case TypedObjectTypingResult(fields, _, _) =>
        Json.fromFields(fields.toList.map { case (fieldName, fieldValue) =>
          fieldName -> fieldValue.toValuePlacedInPlaceholder
        })
      // primitive types
      case TypedClass(clazz, _) if StandardTypesClasses.isDecimalNumber(clazz) =>
        Json.fromInt(placeholderValueForIntegerNumber)
      case TypedClass(clazz, _) if StandardTypesClasses.isFloatingPointNumber(clazz) =>
        Json.fromDoubleOrNull(placeholderValueForFloatingPointNumber)
      case TypedClass(BooleanClass, _) => Json.fromBoolean(placeholderValueForBoolean)
      // strings and templating logic
      case TypedClass(StringClass, _) =>
        Json.fromString(placeholderValueForStringAndTemplatingLogic)
      // logical types
      case TypeMarkedUsingSpecialMarker(specialMarkerValue) =>
        Json.fromString(specialMarkerValue)
      // For now, for more complex types we use a number, because we don't want to break the validation and numbers are acceptable in most places
      case _ => Json.fromBigDecimal(specialMarkerForUnknownTypes)
    }

  }

}
