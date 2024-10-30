package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, StaticMethodDefinition}

import java.lang.{Boolean => JBoolean}

class ToBooleanConversionExt(target: Any) {
  def isBoolean(): JBoolean       = ToBooleanConversionExt.canConvert(target)
  def toBoolean(): JBoolean       = ToBooleanConversionExt.convert(target)
  def toBooleanOrNull(): JBoolean = ToBooleanConversionExt.convertOrNull(target)
}

object ToBooleanConversionExt extends ConversionExt {
  private val cannotConvertException = (value: Any) =>
    new IllegalArgumentException(s"Cannot convert: $value to Boolean")
  private val allowedClassesForConversion: Set[Class[_]] = Set(classOf[String], classOf[Object])
  private val booleanTyping: typing.TypedClass           = Typed.typedClass[JBoolean]

  override val definitions: List[StaticMethodDefinition] = List(
    definition(booleanTyping, "isBoolean", Some("Check whether can be convert to a Boolean")),
    definition(booleanTyping, "toBoolean", Some("Convert to Boolean or throw exception in case of failure")),
    definition(booleanTyping, "toBooleanOrNull", Some("Convert to Boolean or null in case of failure")),
  )

  override type ExtensionMethodInvocationTarget = ToBooleanConversionExt
  override val invocationTargetClass: Class[ToBooleanConversionExt] = classOf[ToBooleanConversionExt]

  override def createConverter(
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[ToBooleanConversionExt] =
    (target: Any) => new ToBooleanConversionExt(target)

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
