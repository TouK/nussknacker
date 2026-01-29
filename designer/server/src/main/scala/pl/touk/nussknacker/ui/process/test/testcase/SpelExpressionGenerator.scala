package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses._
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.util.ReflectUtils.JavaEnumConstants
import pl.touk.nussknacker.engine.util.Implicits.RichScalaListMap

import scala.annotation.tailrec
import scala.collection.immutable.ListMap

private object SpelExpressionGenerator {

  def generate(typ: TypingResult): Option[String] = {
    generateForTypingResult(typ, indentLevel = 0)
  }

  @tailrec
  private def generateForTypingResult(typ: TypingResult, indentLevel: Int): Option[String] = typ match {
    case _: Unknown =>
      Some("null")

    case TypedNull =>
      Some("null")

    case TypedTaggedValue(underlying, _) =>
      generateForTypingResult(underlying, indentLevel)

    case TypedObjectWithValue(underlying, _) =>
      generateForTypingResult(underlying, indentLevel)

    case klass: TypedClass =>
      generateForClass(klass, indentLevel)

    case union: TypedUnion =>
      generateForTypingResult(union.possibleTypes.head, indentLevel)

    case TypedObjectTypingResult(fields, _, _) =>
      generateForRecord(fields, indentLevel)

    case _: TypedDict =>
      None
  }

  private def generateForClass(klass: TypedClass, indentLevel: Int): Option[String] = klass match {
    case TypedClass(clazz, _) if clazz == StringClass =>
      Some("''")

    case TypedClass(clazz, _) if clazz == BooleanClass =>
      Some("true")

    case TypedClass(clazz, _) if clazz == LongClass =>
      Some("0l")

    case TypedClass(clazz, _) if clazz == FloatClass =>
      Some("0.0f")

    case TypedClass(clazz, _) if clazz == DoubleClass =>
      Some("0.0")

    case TypedClass(clazz, _) if isDecimalNumber(clazz) =>
      Some("0")

    case TypedClass(ListClass | ArrayClass, elementType :: Nil) =>
      generateForTypingResult(elementType, indentLevel) match {
        case Some(elementExpr) => Some(s"{$elementExpr}")
        case None              => Some("{}")
      }

    case TypedClass(JavaEnumConstants(firstEnumConstant :: _), _) =>
      Some(s"T(${klass.klass.getName}).${firstEnumConstant.name()}")

    case TypedClass(clazz, _) if clazz == InstantClass =>
      Some("T(java.time.Instant).parse('1900-01-01T00:00:00Z')")

    case TypedClass(clazz, _) if clazz == LocalDateTimeClass =>
      Some("T(java.time.LocalDateTime).parse('1900-01-01T00:00:00')")

    case TypedClass(clazz, _) if clazz == LocalDateClass =>
      Some("T(java.time.LocalDate).parse('1900-01-01')")

    case TypedClass(clazz, _) if clazz == LocalTimeClass =>
      Some("T(java.time.LocalTime).parse('00:00:00')")

    case TypedClass(clazz, _) if clazz == UUIDClass =>
      Some("T(java.util.UUID).fromString('00000000-0000-0000-0000-000000000000')")

    case _ =>
      None
  }

  private def isDecimalNumber(clazz: Class[_]): Boolean = {
    clazz == classOf[java.lang.Integer] ||
    clazz == classOf[java.lang.Long] ||
    clazz == classOf[java.lang.Short] ||
    clazz == classOf[java.lang.Byte] ||
    clazz == classOf[java.math.BigInteger] ||
    clazz == classOf[java.math.BigDecimal]
  }

  private def isFloatingPointNumber(clazz: Class[_]): Boolean = {
    clazz == classOf[java.lang.Float] ||
    clazz == classOf[java.lang.Double]
  }

  private def generateForRecord(fields: ListMap[String, TypingResult], indentLevel: Int): Option[String] = {
    val fieldExpressions = fields.mapValuesNow(generateForTypingResult(_, indentLevel + 1).getOrElse("null"))
    val recordExpression = if (fieldExpressions.isEmpty) {
      "{:}"
    } else if (fieldExpressions.size == 1) {
      fieldExpressions.map { case (key, value) => s"$key: $value" }.mkString("{", ", ", "}")
    } else {
      val indent        = "  " * (indentLevel + 1)
      val closingIndent = "  " * indentLevel
      val fieldLines    = fieldExpressions.map { case (key, value) => s"$indent$key: $value" }.mkString(",\n")
      s"{\n$fieldLines\n$closingIndent}"
    }
    Some(recordExpression)
  }

}
