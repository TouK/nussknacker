package pl.touk.nussknacker.engine.extension

import org.springframework.util.NumberUtils
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.extension.Conversion.toNumberEither

import java.lang.{Long => JLong}
import scala.util.Try

class ToLongConversionExt extends ExtensionMethodHandler {

  override val methodRegistry: Map[String, ExtensionMethod] = Map(
    "isLong"       -> ((target: Any, _) => ToLongConversionExt.canConvert(target)),
    "toLong"       -> ((target: Any, _) => ToLongConversionExt.convert(target)),
    "toLongOrNull" -> ((target: Any, _) => ToLongConversionExt.convertOrNull(target)),
  )

}

object ToLongConversionExt extends ConversionExt with ToNumericConversion {
  override type ResultType = JLong
  override val resultTypeClass: Class[JLong] = classOf[JLong]

  override def createHandler(set: ClassDefinitionSet): ExtensionMethodHandler = new ToLongConversionExt

  override def convertEither(value: Any): Either[Throwable, JLong] = {
    value match {
      case v: Number => Try(NumberUtils.convertNumberToTargetClass(v, resultTypeClass)).toEither
      case v: String => toNumberEither(v).flatMap(convertEither)
      case _         => Left(new IllegalArgumentException(s"Cannot convert: $value to Long"))
    }
  }

}
