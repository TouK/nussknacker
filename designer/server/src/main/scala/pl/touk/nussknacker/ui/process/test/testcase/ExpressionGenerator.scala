package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses._
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.util.ReflectUtils.JavaEnumConstants
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.Implicits.RichScalaListMap

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime}
import java.util.UUID
import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

private object ExpressionGenerator {

  def generate(typ: TypingResult): Option[Expression] = {
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
    case TypedClass(ListClass | ArrayClass, elementType :: Nil) =>
      generateForTypingResult(elementType, indentLevel) match {
        case Some(elementExpr) => Some(s"[$elementExpr]")
        case None              => Some("[]")
      }
    case TypedClass(JavaEnumConstants(firstEnumConstant :: _), _) =>
      Some(s"#{ T(${typedClass.klass.getName}).${firstEnumConstant.name()} }")
    case TypedClass(clazz, _) if clazz == InstantClass =>
      Some("\"1900-01-01T00:00:00Z\"")
    case TypedClass(clazz, _) if clazz == LocalDateTimeClass =>
      Some("\"1900-01-01T00:00:00\"")
    case TypedClass(clazz, _) if clazz == LocalDateClass =>
      Some("\"1900-01-01\"")
    case TypedClass(clazz, _) if clazz == LocalTimeClass =>
      Some("\"00:00:00\"")
    case TypedClass(clazz, _) if clazz == UUIDClass =>
      Some("\"00000000-0000-0000-0000-000000000000\"")
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

  private def generateFromValue(value: Any, underlying: TypedClass, indentLevel: Int): Option[String] = {
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
      case uuid: UUID =>
        Some(s""""${uuid.toString}"""")
      case list: java.util.List[_] =>
        val elements = list.asScala.toList
        if (elements.isEmpty) {
          Some("[]")
        } else {
          underlying.params match {
            case elementType :: Nil =>
              val elementExpressions = elements.flatMap { element =>
                elementType match {
                  case TypedObjectWithValue(elemUnderlying, elemValue) =>
                    generateFromValue(elemValue, elemUnderlying, indentLevel)
                  case elemClass: TypedClass =>
                    generateFromValue(element, elemClass, indentLevel)
                  case _ =>
                    None
                }
              }
              Some(elementExpressions.mkString("[", ", ", "]"))
            case _ =>
              Some("[]")
          }
        }
      case _ =>
        generateForTypingResult(underlying, indentLevel)
    }
  }

}
