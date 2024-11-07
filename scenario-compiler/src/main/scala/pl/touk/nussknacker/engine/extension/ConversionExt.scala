package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition, StaticMethodDefinition}
import pl.touk.nussknacker.engine.extension.ExtensionMethod.NoArg
import pl.touk.nussknacker.engine.util.classes.Extensions.{ClassExtensions, ClassesExtensions}

import java.lang.{Boolean => JBoolean}
import scala.util.matching.Regex

class ConversionExtensionMethodHandler(classesBySimpleName: Map[String, Class[_]])
    extends CastOrConversionExt(classesBySimpleName) {
  private val isRegex       = "^is(\\w+)$".r
  private val toRegex       = "^to(\\w+?)(?<!OrNull)$".r
  private val toOrNullRegex = "^to(\\w+)(?:OrNull)$".r

  override def findMethod(methodName: String, argsSize: Int): Option[ExtensionMethod] = {
    extractMethodWithParam(methodName)
      .flatMap(methodWithParam =>
        super
          .findMethod(methodWithParam._1, 1)
          .map(method => NoArg(target => method.invoke(target, methodWithParam._2)))
      )
  }

  private def extractMethodWithParam(methodName: String): Option[(String, String)] =
    matchAndExtractFirstGroup(methodName, isRegex)
      .map(typeName => "is" -> typeName)
      .orElse(matchAndExtractFirstGroup(methodName, toRegex).map(typeName => "to" -> typeName))
      .orElse(matchAndExtractFirstGroup(methodName, toOrNullRegex).map(typeName => "toOrNull" -> typeName))

  private def matchAndExtractFirstGroup(text: String, regex: Regex): Option[String] = regex
    .findFirstMatchIn(text)
    .map(matched => matched.group(1))

}

trait ConversionExt extends ExtensionMethodsDefinition {
  private lazy val definitionsByName = definitions().groupBy(_.name)

  val conversion: Conversion[_]

  override def createHandler(set: ClassDefinitionSet): ExtensionMethodHandler =
    new ConversionExtensionMethodHandler(set.classDefinitionsMap.keySet.classesByNamesAndSimpleNamesLowerCase())

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] = {
    if (conversion.appliesToConversion(clazz)) {
      definitionsByName
    } else {
      Map.empty
    }
  }

  override def appliesToClassInRuntime(clazz: Class[_]): Boolean = true

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
