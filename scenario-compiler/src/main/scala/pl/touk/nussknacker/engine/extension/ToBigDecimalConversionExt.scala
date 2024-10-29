package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition, StaticMethodDefinition}

import java.lang.{Boolean => JBoolean}
import java.math.{BigDecimal => JBigDecimal, BigInteger => JBigInteger}
import scala.util.Try

class ToBigDecimalConversionExt(target: Any) {

  def isBigDecimal(): JBoolean          = ToBigDecimalConversionExt.canConvert(target)
  def toBigDecimal(): JBigDecimal       = ToBigDecimalConversionExt.convert(target)
  def toBigDecimalOrNull(): JBigDecimal = ToBigDecimalConversionExt.convertOrNull(target)
}

object ToBigDecimalConversionExt extends ExtensionMethodsHandler with NumericConversion {

  private val definitions = List(
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

  override type ExtensionMethodInvocationTarget = ToBigDecimalConversionExt
  override val invocationTargetClass: Class[ToBigDecimalConversionExt] = classOf[ToBigDecimalConversionExt]

  override def createConverter(
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[ToBigDecimalConversionExt] =
    (target: Any) => new ToBigDecimalConversionExt(target)

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] = {
    if (appliesToConversion(clazz)) {
      definitions
    } else {
      Map.empty
    }
  }

  override def appliesToClassInRuntime(clazz: Class[_]): Boolean = true

  override type ResultType = JBigDecimal
  override val resultTypeClass: Class[JBigDecimal] = classOf[JBigDecimal]

  override def convertEither(value: Any): Either[Throwable, JBigDecimal] =
    value match {
      case v: JBigDecimal => Right(v)
      case v: JBigInteger => Right(new JBigDecimal(v))
      case v: String      => Try(new JBigDecimal(v)).toEither
      case v: Number      => Try(new JBigDecimal(v.toString)).toEither
      case _              => Left(new IllegalArgumentException(s"Cannot convert: $value to BigDecimal"))
    }

  private def definition(result: TypingResult, methodName: String, desc: Option[String]) = StaticMethodDefinition(
    signature = MethodTypeInfo.noArgTypeInfo(result),
    name = methodName,
    description = desc
  )

}
