package pl.touk.nussknacker.engine.extension

import org.springframework.util.NumberUtils
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet

import java.lang.{Boolean => JBoolean}
import java.math.{BigDecimal => JBigDecimal, BigInteger => JBigInteger}
import scala.util.Try

class ToBigDecimalConversionExt(target: Any) extends ExtensionMethodInvocationTarget {

  override def invoke(methodName: String, arguments: Array[Object]): Any = methodName match {
    case "isBigDecimal"       => isBigDecimal()
    case "toBigDecimal"       => toBigDecimal()
    case "toBigDecimalOrNull" => toBigDecimalOrNull()
    case _                    => throw new IllegalAccessException(s"Cannot find method with name: '$methodName'")
  }

  def isBigDecimal(): JBoolean          = ToBigDecimalConversionExt.canConvert(target)
  def toBigDecimal(): JBigDecimal       = ToBigDecimalConversionExt.convert(target)
  def toBigDecimalOrNull(): JBigDecimal = ToBigDecimalConversionExt.convertOrNull(target)
}

object ToBigDecimalConversionExt extends ConversionExt[ToBigDecimalConversionExt] with ToNumericConversion {
  override val invocationTargetClass: Class[ToBigDecimalConversionExt] = classOf[ToBigDecimalConversionExt]
  override type ResultType = JBigDecimal
  override val resultTypeClass: Class[JBigDecimal] = classOf[JBigDecimal]

  override def createConverter(
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[ToBigDecimalConversionExt] =
    (target: Any) => new ToBigDecimalConversionExt(target)

  override def convertEither(value: Any): Either[Throwable, JBigDecimal] =
    value match {
      case v: JBigDecimal => Right(v)
      case v: JBigInteger => Right(new JBigDecimal(v))
      case v: Number      => Try(NumberUtils.convertNumberToTargetClass(v, resultTypeClass)).toEither
      case v: String      => Try(new JBigDecimal(v)).toEither
      case _              => Left(new IllegalArgumentException(s"Cannot convert: $value to BigDecimal"))
    }

}
