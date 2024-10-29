package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition, StaticMethodDefinition}

import java.lang.{Boolean => JBoolean, Double => JDouble, Long => JLong}
import java.math.{BigDecimal => JBigDecimal}

class NumericConversionExt(target: Any) {

  def isLong(): JBoolean                = ToLongConversion.canConvert(target)
  def toLong(): JLong                   = ToLongConversion.convert(target)
  def toLongOrNull(): JLong             = ToLongConversion.convertOrNull(target)
  def isDouble(): JBoolean              = ToDoubleConversion.canConvert(target)
  def toDouble(): JDouble               = ToDoubleConversion.convert(target)
  def toDoubleOrNull(): JDouble         = ToDoubleConversion.convertOrNull(target)
  def isBigDecimal(): JBoolean          = ToBigDecimalConversion.canConvert(target)
  def toBigDecimal(): JBigDecimal       = ToBigDecimalConversion.convert(target)
  def toBigDecimalOrNull(): JBigDecimal = ToBigDecimalConversion.convertOrNull(target)
}

object NumericConversionExt extends ExtensionMethodsHandler {

  private val definitions = List(
    definition(Typed.typedClass[JBoolean], "isLong", Some("Check whether can be convert to a Long")),
    definition(Typed.typedClass[JLong], "toLong", Some("Convert to Long or throw exception in case of failure")),
    definition(Typed.typedClass[JLong], "toLongOrNull", Some("Convert to Long or null in case of failure")),
    definition(Typed.typedClass[JBoolean], "isDouble", Some("Check whether can be convert to a Double")),
    definition(Typed.typedClass[JDouble], "toDouble", Some("Convert to Double or throw exception in case of failure")),
    definition(Typed.typedClass[JDouble], "toDoubleOrNull", Some("Convert to Double or null in case of failure")),
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

  override type ExtensionMethodInvocationTarget = NumericConversionExt
  override val invocationTargetClass: Class[NumericConversionExt] = classOf[NumericConversionExt]

  override def createConverter(
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[NumericConversionExt] =
    (target: Any) => new NumericConversionExt(target)

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] = {
    if (ToLongConversion.applies(clazz) || ToBigDecimalConversion.applies(clazz) || ToDoubleConversion.applies(clazz)) {
      definitions
    } else {
      Map.empty
    }
  }

  override def applies(clazz: Class[_]): Boolean = true

  private def definition(result: TypingResult, methodName: String, desc: Option[String]) = StaticMethodDefinition(
    signature = MethodTypeInfo.noArgTypeInfo(result),
    name = methodName,
    description = desc
  )

}
