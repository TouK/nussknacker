package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses._
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.util.ReflectUtils.JavaEnumConstants
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.Implicits.RichScalaListMap

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
import scala.annotation.tailrec
import scala.collection.immutable.ListMap

private object ExpressionGenerator {

  def generateJsonTemplate(typ: TypingResult): Option[Expression] = {
    generateForTypingResult(typ, indentLevel = 0)
      .map(Expression.jsonTemplate)
  }

  @tailrec
  private def generateForTypingResult(typ: TypingResult, indentLevel: Int): Option[String] = typ match {
    case _: Unknown =>
      Some("null")
    case TypedNull =>
      Some("null")
    case TypedTaggedValue(underlying, _) =>
      generateForTypingResult(underlying, indentLevel)
    case TypedObjectWithValue(underlying, value) =>
      generateFromValue(value, underlying, indentLevel)
    case klass: TypedClass =>
      generateForClass(klass, indentLevel)
    case union: TypedUnion =>
      generateForTypingResult(union.possibleTypes.head, indentLevel)
    case TypedObjectTypingResult(fields, _, _) =>
      generateForRecord(fields, indentLevel)
    case _: TypedDict =>
      None
  }

  private def generateForClass(typedClass: TypedClass, indentLevel: Int): Option[String] = typedClass match {
    case TypedClass(clazz, _) if clazz == StringClass =>
      Some("\"\"")
    case TypedClass(clazz, _) if clazz == BooleanClass =>
      Some("true")
    case TypedClass(clazz, _) if clazz == LongClass =>
      Some("0")
    case TypedClass(clazz, _) if clazz == FloatClass =>
      Some("0.0")
    case TypedClass(clazz, _) if clazz == DoubleClass =>
      Some("0.0")
    case TypedClass(clazz, _) if clazz == BigDecimalClass =>
      Some("0.0")
    case TypedClass(clazz, _) if clazz == ByteClass =>
      Some("0")
    case TypedClass(clazz, _) if clazz == ShortClass =>
      Some("0")
    case TypedClass(clazz, _) if isDecimalNumber(clazz) =>
      Some("0")
    case TypedClass(MapClass, (keyType: SingleTypingResult) :: valueType :: Nil)
        if keyType.runtimeObjType.klass == StringClass =>
      val valueExpr     = generateForTypingResult(valueType, indentLevel + 1).getOrElse("null")
      val indent        = "  " * (indentLevel + 1)
      val closingIndent = "  " * indentLevel
      Some(s"""{\n$indent"field": $valueExpr\n$closingIndent}""")
    case TypedClass(ListClass | ArrayClass, elementType :: Nil) =>
      generateForTypingResult(elementType, indentLevel) match {
        case Some(elementExpr) => Some(s"[$elementExpr]")
        case None              => Some("[]")
      }
    case TypedClass(JavaEnumConstants(firstEnumConstant :: _), _) =>
      Some(s"#{ T(${typedClass.klass.getName}).${firstEnumConstant.name()} }")
    case TypedClass(clazz, _) if clazz == ZonedDateTimeClass =>
      Some("\"1900-01-01T00:00:00+00:00[UTC]\"")
    case TypedClass(clazz, _) if clazz == OffsetDateTimeClass =>
      Some("\"1900-01-01T00:00:00+00:00\"")
    case TypedClass(clazz, _) if clazz == InstantClass =>
      Some("\"1900-01-01T00:00:00Z\"")
    case TypedClass(clazz, _) if clazz == LocalDateTimeClass =>
      Some("\"1900-01-01T00:00:00\"")
    case TypedClass(clazz, _) if clazz == LocalDateClass =>
      Some("\"1900-01-01\"")
    case TypedClass(clazz, _) if clazz == LocalTimeClass =>
      Some("\"00:00:00\"")
    case TypedClass(clazz, _) if clazz == DurationClass =>
      Some("\"PT0H\"")
    case TypedClass(clazz, _) if clazz == PeriodClass =>
      Some("\"P0D\"")
    case TypedClass(clazz, _) if clazz == ZoneOffsetClass =>
      Some("\"+00:00\"")
    case TypedClass(clazz, _) if clazz == ZoneIdClass =>
      Some("\"UTC\"")
    case TypedClass(clazz, _) if clazz == UUIDClass =>
      Some("\"00000000-0000-0000-0000-000000000000\"")
    case TypedClass(clazz, _) if clazz == CurrencyClass =>
      Some("\"USD\"")
    case TypedClass(clazz, _) if clazz == LocaleClass =>
      Some("\"en\"")
    case TypedClass(clazz, _) if clazz == CharsetClass =>
      Some("\"UTF-8\"")
    case _ =>
      None
  }

  private def generateForRecord(fields: ListMap[String, TypingResult], indentLevel: Int): Option[String] = {
    val fieldExpressions = fields.mapValuesNow(generateForTypingResult(_, indentLevel + 1).getOrElse("null"))
    val recordExpression = if (fieldExpressions.isEmpty) {
      "{}"
    } else {
      val indent        = "  " * (indentLevel + 1)
      val closingIndent = "  " * indentLevel
      val fieldLines    = fieldExpressions.map { case (key, value) => s"""$indent"$key": $value""" }.mkString(",\n")
      s"{\n$fieldLines\n$closingIndent}"
    }
    Some(recordExpression)
  }

  private def generateFromValue(value: Any, underlying: TypingResult, indentLevel: Int): Option[String] = {
    value match {
      case null =>
        Some("null")
      case s: String =>
        val escaped = s
          .replace("\\", "\\\\")
          .replace("\"", "\\\"")
          .replace("\n", "\\n")
          .replace("\r", "\\r")
          .replace("\t", "\\t")
        Some(s""""$escaped"""")
      case b: Boolean =>
        Some(b.toString)
      case n: Number =>
        Some(n.toString)
      case instant: Instant =>
        Some(s""""${instant.toString}"""")
      case localDateTime: LocalDateTime =>
        Some(s""""${localDateTime.toString}"""")
      case localDate: LocalDate =>
        Some(s""""${localDate.toString}"""")
      case localTime: LocalTime =>
        Some(s""""${localTime.toString}"""")
      case zonedDateTime: ZonedDateTime =>
        Some(s""""${zonedDateTime.toString}"""")
      case offsetDateTime: OffsetDateTime =>
        Some(s""""${offsetDateTime.toString}"""")
      case duration: Duration =>
        Some(s""""${duration.toString}"""")
      case period: Period =>
        Some(s""""${period.toString}"""")
      case zoneOffset: ZoneOffset =>
        Some(s""""${zoneOffset.toString}"""")
      case zoneId: ZoneId =>
        Some(s""""${zoneId.toString}"""")
      case uuid: UUID =>
        Some(s""""${uuid.toString}"""")
      case currency: Currency =>
        Some(s""""${currency.toString}"""")
      case locale: Locale =>
        Some(s""""${locale.toString}"""")
      case charset: Charset =>
        Some(s""""${charset.toString}"""")
      // We could also handle collections and maps (records) here
      case _ =>
        generateForTypingResult(underlying, indentLevel)
    }
  }

}
