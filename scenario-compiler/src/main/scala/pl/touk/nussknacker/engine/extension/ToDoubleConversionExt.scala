package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition, StaticMethodDefinition}
import pl.touk.nussknacker.engine.extension.Conversion.toNumber

import java.lang.{Boolean => JBoolean, Double => JDouble}
import scala.util.Try

class ToDoubleConversionExt(target: Any) {

  def isDouble(): JBoolean      = ToDoubleConversionExt.canConvert(target)
  def toDouble(): JDouble       = ToDoubleConversionExt.convert(target)
  def toDoubleOrNull(): JDouble = ToDoubleConversionExt.convertOrNull(target)
}

object ToDoubleConversionExt extends ExtensionMethodsHandler with NumericConversion {

  private val definitions = List(
    definition(Typed.typedClass[JBoolean], "isDouble", Some("Check whether can be convert to a Double")),
    definition(Typed.typedClass[JDouble], "toDouble", Some("Convert to Double or throw exception in case of failure")),
    definition(Typed.typedClass[JDouble], "toDoubleOrNull", Some("Convert to Double or null in case of failure")),
  ).groupBy(_.name)

  override type ExtensionMethodInvocationTarget = ToDoubleConversionExt
  override val invocationTargetClass: Class[ToDoubleConversionExt] = classOf[ToDoubleConversionExt]

  override def createConverter(
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[ToDoubleConversionExt] =
    (target: Any) => new ToDoubleConversionExt(target)

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] = {
    if (appliesToConversion(clazz)) {
      definitions
    } else {
      Map.empty
    }
  }

  override def appliesToClassInRuntime(clazz: Class[_]): Boolean = true

  override type ResultType = JDouble
  override val resultTypeClass: Class[JDouble] = classOf[JDouble]

  override def convertEither(value: Any): Either[Throwable, JDouble] = {
    value match {
      case v: Number => Right(v.doubleValue())
      case v: String => Try(JDouble.valueOf(toNumber(v).doubleValue())).toEither
      case _         => Left(new IllegalArgumentException(s"Cannot convert: $value to Double"))
    }
  }

  private def definition(result: TypingResult, methodName: String, desc: Option[String]) = StaticMethodDefinition(
    signature = MethodTypeInfo.noArgTypeInfo(result),
    name = methodName,
    description = desc
  )

}
