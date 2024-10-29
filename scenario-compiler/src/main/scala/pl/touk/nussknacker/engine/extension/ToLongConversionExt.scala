package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition, StaticMethodDefinition}
import pl.touk.nussknacker.engine.extension.Conversion.toNumber

import java.lang.{Boolean => JBoolean, Long => JLong}
import scala.util.Try

class ToLongConversionExt(target: Any) {

  def isLong(): JBoolean    = ToLongConversionExt.canConvert(target)
  def toLong(): JLong       = ToLongConversionExt.convert(target)
  def toLongOrNull(): JLong = ToLongConversionExt.convertOrNull(target)
}

object ToLongConversionExt extends ExtensionMethodsHandler with NumericConversion {

  private val definitions = List(
    definition(Typed.typedClass[JBoolean], "isLong", Some("Check whether can be convert to a Long")),
    definition(Typed.typedClass[JLong], "toLong", Some("Convert to Long or throw exception in case of failure")),
    definition(Typed.typedClass[JLong], "toLongOrNull", Some("Convert to Long or null in case of failure")),
  ).groupBy(_.name)

  override type ExtensionMethodInvocationTarget = ToLongConversionExt
  override val invocationTargetClass: Class[ToLongConversionExt] = classOf[ToLongConversionExt]

  override def createConverter(
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[ToLongConversionExt] =
    (target: Any) => new ToLongConversionExt(target)

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] = {
    if (appliesToConversion(clazz)) {
      definitions
    } else {
      Map.empty
    }
  }

  override def appliesToClassInRuntime(clazz: Class[_]): Boolean = true

  override type ResultType = JLong
  override val resultTypeClass: Class[JLong] = classOf[JLong]

  override def convertEither(value: Any): Either[Throwable, JLong] = {
    value match {
      case v: Number => Right(v.longValue())
      case v: String => Try(JLong.valueOf(toNumber(v).longValue())).toEither
      case _         => Left(new IllegalArgumentException(s"Cannot convert: $value to Long"))
    }
  }

  private def definition(result: TypingResult, methodName: String, desc: Option[String]) = StaticMethodDefinition(
    signature = MethodTypeInfo.noArgTypeInfo(result),
    name = methodName,
    description = desc
  )

}
