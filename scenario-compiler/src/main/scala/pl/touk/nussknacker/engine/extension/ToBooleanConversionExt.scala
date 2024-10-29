package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition, StaticMethodDefinition}

import java.lang.{Boolean => JBoolean}

class BooleanConversionExt(target: Any) {
  def isBoolean(): JBoolean       = ToBooleanConversion.canConvert(target)
  def toBoolean(): JBoolean       = ToBooleanConversion.convert(target)
  def toBooleanOrNull(): JBoolean = ToBooleanConversion.convertOrNull(target)
}

object BooleanConversionExt extends ExtensionMethodsHandler {

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

  override type ExtensionMethodInvocationTarget = BooleanConversionExt
  override val invocationTargetClass: Class[BooleanConversionExt] = classOf[BooleanConversionExt]

  override def createConverter(
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[BooleanConversionExt] =
    (target: Any) => new BooleanConversionExt(target)

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] =
    if (ToBooleanConversion.applies(clazz)) definitions
    else Map.empty

  override def applies(clazz: Class[_]): Boolean = true
}
