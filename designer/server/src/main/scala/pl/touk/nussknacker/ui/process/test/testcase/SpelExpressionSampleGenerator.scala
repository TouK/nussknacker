package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses._
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.util.ReflectUtils.JavaEnumConstants
import pl.touk.nussknacker.engine.util.Implicits.RichScalaListMap

object SpelExpressionSampleGenerator {

  def generateSampleExpression(typ: TypingResult): Option[String] = typ match {
    case TypedObjectTypingResult(fields, _, _) =>
      val fieldExpressions = fields.mapValuesNow(generateSampleExpression(_).getOrElse("null"))
      val fieldsString     = fieldExpressions.map { case (key, value) => s"$key: $value" }.mkString(", ")
      Some(s"{$fieldsString}")

    case TypedTaggedValue(underlying, _) =>
      generateSampleExpression(underlying)

    case TypedObjectWithValue(_, value) =>
      generateSampleExpressionForValue(value)

    case TypedNull =>
      Some("null")

    case klass: TypedClass =>
      generateSampleExpressionForClass(klass)

    case union: TypedUnion =>
      generateSampleExpression(union.possibleTypes.head)

    case _: Unknown =>
      None

    case _: TypedDict =>
      Some("{:}")
  }

  private def generateSampleExpressionForValue(value: Any): Option[String] = value match {
    case s: String  => Some(s"'$s'")
    case i: Int     => Some(i.toString)
    case l: Long    => Some(l.toString)
    case d: Double  => Some(d.toString)
    case f: Float   => Some(f.toString)
    case b: Boolean => Some(b.toString)
    case null       => Some("null")
    case _          => None
  }

  private def generateSampleExpressionForClass(klass: TypedClass): Option[String] = klass match {
    case TypedClass(clazz, _) if clazz == StringClass =>
      Some("'string'")

    case TypedClass(clazz, _) if clazz == BooleanClass =>
      Some("true")

    case TypedClass(clazz, _) if isDecimalNumber(clazz) =>
      Some("42")

    case TypedClass(clazz, _) if isFloatingPointNumber(clazz) =>
      Some("42.0")

    case TypedClass(ListClass | ArrayClass, elementType :: Nil) =>
      generateSampleExpression(elementType) match {
        case Some(elementExpr) => Some(s"{$elementExpr}")
        case None              => Some("{}")
      }

    case TypedClass(MapClass, _ :: valueType :: Nil) =>
      generateSampleExpression(valueType) match {
        case Some(valueExpr) => Some(s"{'key': $valueExpr}")
        case None            => Some("{:}")
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

}
