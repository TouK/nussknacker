package pl.touk.nussknacker.engine.extension

import org.springframework.util.NumberUtils
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.extension.Conversion.toNumberEither

import java.lang.{Boolean => JBoolean, Long => JLong}
import scala.util.Try

class ToLongConversionExt(target: Any) {

  def isLong(): JBoolean    = ToLongConversionExt.canConvert(target)
  def toLong(): JLong       = ToLongConversionExt.convert(target)
  def toLongOrNull(): JLong = ToLongConversionExt.convertOrNull(target)
}

object ToLongConversionExt extends ConversionExt with ToNumericConversion {
  override type ExtensionMethodInvocationTarget = ToLongConversionExt
  override val invocationTargetClass: Class[ToLongConversionExt] = classOf[ToLongConversionExt]
  override type ResultType = JLong
  override val resultTypeClass: Class[JLong] = classOf[JLong]

  override def createConverter(
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[ToLongConversionExt] =
    (target: Any) => new ToLongConversionExt(target)

  override def convertEither(value: Any): Either[Throwable, JLong] = {
    value match {
      case v: Number => Try(NumberUtils.convertNumberToTargetClass(v, resultTypeClass)).toEither
      case v: String => toNumberEither(v).flatMap(convertEither)
      case _         => Left(new IllegalArgumentException(s"Cannot convert: $value to Long"))
    }
  }

}
