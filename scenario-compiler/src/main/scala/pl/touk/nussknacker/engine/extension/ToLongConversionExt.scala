package pl.touk.nussknacker.engine.extension

import org.springframework.util.NumberUtils
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition}
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

  override val definitions: List[MethodDefinition] = List(
    definition(Typed.typedClass[JBoolean], "isLong", Some("Check whether can be convert to a Long")),
    definition(Typed.typedClass[JLong], "toLong", Some("Convert to Long or throw exception in case of failure")),
    definition(Typed.typedClass[JLong], "toLongOrNull", Some("Convert to Long or null in case of failure")),
  )

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
