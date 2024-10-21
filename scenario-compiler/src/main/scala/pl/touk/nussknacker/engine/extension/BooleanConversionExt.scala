package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition, StaticMethodDefinition}

import java.lang.{Boolean => JBoolean}

class BooleanConversionExt(target: Any) {
  private lazy val cannotConvertException = new IllegalArgumentException(s"Cannot convert: $target to Boolean")

  def isBoolean(): JBoolean = convertToBoolean(target).isRight

  def toBoolean(): JBoolean = convertToBoolean(target) match {
    case Right(value) => value
    case Left(ex)     => throw ex
  }

  def toBooleanOrNull(): JBoolean = convertToBoolean(target) match {
    case Right(value) => value
    case Left(_)      => null
  }

  private def convertToBoolean(value: Any): Either[Throwable, JBoolean] = value match {
    case s: String =>
      stringToBoolean(s).toRight(cannotConvertException)
    case b: JBoolean => Right(b)
    case _           => Left(cannotConvertException)
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

object BooleanConversionExt extends ExtensionMethodsHandler {
  private val stringClass                                 = classOf[String]
  private val unknownClass                                = classOf[Object]
  private val allowedClassesForDefinitions: Set[Class[_]] = Set(stringClass, unknownClass)
  private val booleanTyping                               = Typed.typedClass[JBoolean]

  private val definition = StaticMethodDefinition(
    signature = MethodTypeInfo(
      noVarArgs = Nil,
      varArg = None,
      result = booleanTyping
    ),
    name = "",
    description = None
  )

  private val definitions = List(
    definition.copy(name = "isBoolean", description = Some("Check whether can be convert to a Boolean")),
    definition.copy(name = "toBoolean", description = Some("Convert to Boolean or throw exception in case of failure")),
    definition.copy(name = "toBooleanOrNull", description = Some("Convert to Boolean or null in case of failure")),
  ).groupBy(_.name)

  override type ExtensionMethodInvocationTarget = BooleanConversionExt
  override val invocationTargetClass: Class[BooleanConversionExt] = classOf[BooleanConversionExt]

  override def createConverter(
      classLoader: ClassLoader,
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[BooleanConversionExt] =
    (target: Any) => new BooleanConversionExt(target)

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] =
    if (allowedClassesForDefinitions.contains(clazz)) definitions
    else Map.empty

  override def applies(clazz: Class[_]): Boolean = true
}
