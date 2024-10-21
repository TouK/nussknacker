package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition, StaticMethodDefinition}
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassExtensions

import java.lang.{Boolean => JBoolean, Double => JDouble, Long => JLong, Number => JNumber}
import java.math.{BigDecimal => JBigDecimal}

class NumericConversionExt(target: Any) {

  def isLong(): JBoolean                = LongConversion.canConvert(target)
  def toLong(): JLong                   = LongConversion.convert(target)
  def toLongOrNull(): JLong             = LongConversion.convertOrNull(target)
  def isDouble(): JBoolean              = DoubleConversion.canConvert(target)
  def toDouble(): JDouble               = DoubleConversion.convert(target)
  def toDoubleOrNull(): JDouble         = DoubleConversion.convertOrNull(target)
  def isBigDecimal(): JBoolean          = BigDecimalConversion.canConvert(target)
  def toBigDecimal(): JBigDecimal       = BigDecimalConversion.convert(target)
  def toBigDecimalOrNull(): JBigDecimal = BigDecimalConversion.convertOrNull(target)
}

object NumericConversionExt extends ExtensionMethodsHandler {
  private val numberClass  = classOf[JNumber]
  private val stringClass  = classOf[String]
  private val unknownClass = classOf[Object]

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

  override def createConverter(): ToExtensionMethodInvocationTargetConverter[NumericConversionExt] =
    (target: Any) => new NumericConversionExt(target)

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] = {
    if (clazz.isAOrChildOf(numberClass) || clazz == stringClass || clazz == unknownClass) {
      definitions
    } else {
      Map.empty
    }
  }

  override def applies(clazz: Class[_]): Boolean = true

  private def definition(result: TypingResult, methodName: String, desc: Option[String]) = StaticMethodDefinition(
    signature = MethodTypeInfo(
      noVarArgs = Nil,
      varArg = None,
      result = result
    ),
    name = methodName,
    description = desc
  )

}
