package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition, StaticMethodDefinition}
import pl.touk.nussknacker.engine.extension.ExtensionMethod.NoArg
import pl.touk.nussknacker.engine.util.classes.Extensions.{ClassExtensions, ClassesExtensions}

import java.lang.{Boolean => JBoolean}

class ConversionExtensionMethodHandler(targetTypeName: String, castOrConversionExt: CastOrConversionExt)
    extends ExtensionMethodHandler {

  override val methodRegistry: Map[String, ExtensionMethod[_]] = castOrConversionExt.methodRegistry.map {
    case (methodName, extensionMethod) =>
      mapMethodName(methodName) -> NoArg(target => extensionMethod.invoke(target, targetTypeName))
  }

  private def mapMethodName(methodName: String): String = methodName match {
    case "is"       => s"is$targetTypeName"
    case "to"       => s"to$targetTypeName"
    case "toOrNull" => s"to${targetTypeName}OrNull"
    case _          => throw new IllegalArgumentException(s"Method $methodName not implemented")
  }

}

trait ConversionExt extends ExtensionMethodsDefinition {
  private lazy val definitionsByName = definitions().groupBy(_.name)

  val conversion: Conversion[_]

  override def createHandler(clazz: Class[_], set: ClassDefinitionSet): Option[ExtensionMethodHandler] =
    if (appliesToClassInRuntime(clazz)) {
      Some(
        new ConversionExtensionMethodHandler(
          conversion.resultTypeClass.simpleName(),
          new CastOrConversionExt(set.classDefinitionsMap.keySet.classesByNamesAndSimpleNamesLowerCase())
        )
      )
    } else {
      None
    }

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] = {
    if (conversion.appliesToConversion(clazz)) {
      definitionsByName
    } else {
      Map.empty
    }
  }

  def definitions(): List[MethodDefinition] = {
    val targetTypeSimpleName = conversion.resultTypeClass.simpleName()
    List(
      definition(
        Typed.typedClass[JBoolean],
        s"is$targetTypeSimpleName",
        Some(s"Check whether the value can be convert to a $targetTypeSimpleName")
      ),
      definition(
        conversion.typingResult,
        s"to$targetTypeSimpleName",
        Some(s"Convert the value to $targetTypeSimpleName or throw exception in case of failure")
      ),
      definition(
        conversion.typingResult,
        s"to${targetTypeSimpleName}OrNull",
        Some(s"Convert the value to $targetTypeSimpleName or null in case of failure")
      ),
    )
  }

  private def appliesToClassInRuntime(clazz: Class[_]): Boolean = true

  private[extension] def definition(result: TypingResult, methodName: String, desc: Option[String]) =
    StaticMethodDefinition(
      signature = MethodTypeInfo.noArgTypeInfo(result),
      name = methodName,
      description = desc
    )

}

object ConversionExt {

  def apply(conversionParam: Conversion[_]): ConversionExt = new ConversionExt {
    override val conversion: Conversion[_] = conversionParam
  }

}
