package pl.touk.nussknacker.engine.extension

import org.springframework.util.NumberUtils
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition}

import java.lang.{Boolean => JBoolean}
import java.math.{BigDecimal => JBigDecimal, BigInteger => JBigInteger}
import scala.util.Try

class ToBigDecimalConversionExt(target: Any) {

  def isBigDecimal(): JBoolean          = ToBigDecimalConversionExt.canConvert(target)
  def toBigDecimal(): JBigDecimal       = ToBigDecimalConversionExt.convert(target)
  def toBigDecimalOrNull(): JBigDecimal = ToBigDecimalConversionExt.convertOrNull(target)
}

object ToBigDecimalConversionExt extends ToNumericConversionExt {
  override type ExtensionMethodInvocationTarget = ToBigDecimalConversionExt
  override val invocationTargetClass: Class[ToBigDecimalConversionExt] = classOf[ToBigDecimalConversionExt]
  override type ResultType = JBigDecimal
  override val resultTypeClass: Class[JBigDecimal] = classOf[JBigDecimal]

  override val definitions: Map[String, List[MethodDefinition]] = List(
    definition(Typed.typedClass[JBoolean], "isBigDecimal", Some("Check whether can be convert to a BigDecimal")),
    definition(
      Typed.typedClass[JBigDecimal],
      "toBigDecimal",
      Some("Convert to BigDecimal or throw exception in case of failure")
    ),
    definition(
      Typed.typedClass[JBigDecimal],
      "toBigDecimalOrNull",
      Some("Convert to BigDecimal or null in case of failure")
    ),
  ).groupBy(_.name)

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
