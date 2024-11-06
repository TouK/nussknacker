package pl.touk.nussknacker.engine.extension

import org.springframework.util.NumberUtils
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.extension.Conversion.toNumberEither

import java.lang.{Double => JDouble}

class ToDoubleConversionExt extends ExtensionMethodHandler {

  override val methodRegistry: Map[String, ExtensionMethod] = Map(
    "isDouble"       -> ((target: Any, _) => ToDoubleConversionExt.canConvert(target)),
    "toDouble"       -> ((target: Any, _) => ToDoubleConversionExt.convert(target)),
    "toDoubleOrNull" -> ((target: Any, _) => ToDoubleConversionExt.convertOrNull(target)),
  )

}

object ToDoubleConversionExt extends ConversionExt with ToNumericConversion {
  override type ResultType = JDouble
  override val resultTypeClass: Class[JDouble] = classOf[JDouble]

  override def createHandler(set: ClassDefinitionSet): ExtensionMethodHandler = new ToDoubleConversionExt

  override def convertEither(value: Any): Either[Throwable, JDouble] = {
    value match {
      case v: Number => Right(NumberUtils.convertNumberToTargetClass(v, resultTypeClass))
      case v: String => toNumberEither(v).flatMap(convertEither)
      case _         => Left(new IllegalArgumentException(s"Cannot convert: $value to Double"))
    }
  }

}
