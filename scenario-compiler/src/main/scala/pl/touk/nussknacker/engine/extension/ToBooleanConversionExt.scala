package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition, StaticMethodDefinition}

import java.lang.{Boolean => JBoolean}

class ToBooleanConversionExt(target: Any) {
  def isBoolean(): JBoolean       = ToBooleanConversionExt.canConvert(target)
  def toBoolean(): JBoolean       = ToBooleanConversionExt.convert(target)
  def toBooleanOrNull(): JBoolean = ToBooleanConversionExt.convertOrNull(target)
}

object ToBooleanConversionExt extends ExtensionMethodsHandler with Conversion {
  private val cannotConvertException = (value: Any) =>
    new IllegalArgumentException(s"Cannot convert: $value to Boolean")
  private val allowedClassesForConversion: Set[Class[_]] = Set(classOf[String], classOf[Object])

  private val definition = StaticMethodDefinition(
    signature = MethodTypeInfo.noArgTypeInfo(Typed.typedClass[JBoolean]),
    name = "",
    description = None
  )

  private val definitions = List(
    definition.copy(name = "isBoolean", description = Some("Check whether can be convert to a Boolean")),
    definition.copy(name = "toBoolean", description = Some("Convert to Boolean or throw exception in case of failure")),
    definition.copy(name = "toBooleanOrNull", description = Some("Convert to Boolean or null in case of failure")),
  ).groupBy(_.name)

  override type ExtensionMethodInvocationTarget = ToBooleanConversionExt
  override val invocationTargetClass: Class[ToBooleanConversionExt] = classOf[ToBooleanConversionExt]

  override def createConverter(
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[ToBooleanConversionExt] =
    (target: Any) => new ToBooleanConversionExt(target)

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] =
    if (appliesToConversion(clazz)) definitions
    else Map.empty

  override def appliesToClassInRuntime(clazz: Class[_]): Boolean = true

  override type ResultType = JBoolean
  override val resultTypeClass: Class[JBoolean] = classOf[JBoolean]

  override def appliesToConversion(clazz: Class[_]): Boolean = allowedClassesForConversion.contains(clazz)

  override def convertEither(value: Any): Either[Throwable, JBoolean] = value match {
    case b: JBoolean => Right(b)
    case s: String   => stringToBoolean(s).toRight(cannotConvertException(value))
    case _           => Left(cannotConvertException(value))
  }

  private def stringToBoolean(value: String): Option[JBoolean] =
    if ("true".equalsIgnoreCase(value)) {
      Some(true)
    } else if ("false".equalsIgnoreCase(value)) {
      Some(false)
    } else {
      None
    }

}
