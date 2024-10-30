package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition, StaticMethodDefinition}

trait ToNumericConversionExt extends ExtensionMethodsHandler with ToNumericConversion {
  val definitions: Map[String, List[MethodDefinition]]

  override def appliesToClassInRuntime(clazz: Class[_]): Boolean = true

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] = {
    if (appliesToConversion(clazz)) {
      definitions
    } else {
      Map.empty
    }
  }

  private[extension] def definition(result: TypingResult, methodName: String, desc: Option[String]) =
    StaticMethodDefinition(
      signature = MethodTypeInfo.noArgTypeInfo(result),
      name = methodName,
      description = desc
    )

}
