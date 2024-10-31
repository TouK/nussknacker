package pl.touk.nussknacker.engine.extension

import org.springframework.util.NumberUtils
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.extension.Conversion.toNumberEither

import java.lang.{Boolean => JBoolean, Double => JDouble}

class ToDoubleConversionExt(target: Any) {

  def isDouble(): JBoolean      = ToDoubleConversionExt.canConvert(target)
  def toDouble(): JDouble       = ToDoubleConversionExt.convert(target)
  def toDoubleOrNull(): JDouble = ToDoubleConversionExt.convertOrNull(target)
}

object ToDoubleConversionExt extends ConversionExt with ToNumericConversion {
  override type ExtensionMethodInvocationTarget = ToDoubleConversionExt
  override val invocationTargetClass: Class[ToDoubleConversionExt] = classOf[ToDoubleConversionExt]
  override type ResultType = JDouble
  override val resultTypeClass: Class[JDouble] = classOf[JDouble]

  override def createConverter(
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[ToDoubleConversionExt] =
    (target: Any) => new ToDoubleConversionExt(target)

  override def convertEither(value: Any): Either[Throwable, JDouble] = {
    value match {
      case v: Number => Right(NumberUtils.convertNumberToTargetClass(v, resultTypeClass))
      case v: String => toNumberEither(v).flatMap(convertEither)
      case _         => Left(new IllegalArgumentException(s"Cannot convert: $value to Double"))
    }
  }

}
