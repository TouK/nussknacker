package pl.touk.nussknacker.engine.extension

import org.springframework.util.NumberUtils
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet

import java.math.{BigDecimal => JBigDecimal, BigInteger => JBigInteger}
import scala.util.Try

class ToBigDecimalConversionExt extends ExtensionMethodHandler {

  override val methodRegistry: Map[String, ExtensionMethod] = Map(
    "isBigDecimal"       -> ((target: Any, _) => ToBigDecimalConversionExt.canConvert(target)),
    "toBigDecimal"       -> ((target: Any, _) => ToBigDecimalConversionExt.convert(target)),
    "toBigDecimalOrNull" -> ((target: Any, _) => ToBigDecimalConversionExt.convertOrNull(target)),
  )

}

object ToBigDecimalConversionExt extends ConversionExt with ToNumericConversion {
  override type ResultType = JBigDecimal
  override val resultTypeClass: Class[JBigDecimal] = classOf[JBigDecimal]

  override def createHandler(set: ClassDefinitionSet): ExtensionMethodHandler = new ToBigDecimalConversionExt

  override def convertEither(value: Any): Either[Throwable, JBigDecimal] =
    value match {
      case v: JBigDecimal => Right(v)
      case v: JBigInteger => Right(new JBigDecimal(v))
      case v: Number      => Try(NumberUtils.convertNumberToTargetClass(v, resultTypeClass)).toEither
      case v: String      => Try(new JBigDecimal(v)).toEither
      case _              => Left(new IllegalArgumentException(s"Cannot convert: $value to BigDecimal"))
    }

}
