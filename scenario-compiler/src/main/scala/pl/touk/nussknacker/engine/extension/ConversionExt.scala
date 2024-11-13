package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition, StaticMethodDefinition}
import pl.touk.nussknacker.engine.extension.ExtensionMethod.NoArg
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassExtensions

import java.lang.{Boolean => JBoolean}

class ConversionExt(conversion: Conversion[_]) extends ExtensionMethodsDefinition {

  private lazy val definitionsByName = definitions().groupBy(_.name)

  private lazy val targetTypeName = conversion.resultTypeClass.simpleName()

  private val isMethodName       = s"is$targetTypeName"
  private val toMethodName       = s"to$targetTypeName"
  private val toOrNullMethodName = s"to${targetTypeName}OrNull"

  override def findMethod(
      clazz: Class[_],
      methodName: String,
      argsSize: Int,
      set: ClassDefinitionSet
  ): Option[ExtensionMethod[_]] =
    if (conversion.appliesToConversion(clazz)) {
      for {
        mappedMethodName <- mapMethodName(methodName)
        underlyingMethod <- CastOrConversionExt.findMethod(clazz, mappedMethodName, 1, set)
        resultMethod = NoArg(target => underlyingMethod.invoke(target, targetTypeName))
      } yield resultMethod
    } else {
      None
    }

  private def mapMethodName(methodName: String): Option[String] = methodName match {
    case `isMethodName`       => Some(CastOrConversionExt.isMethodName)
    case `toMethodName`       => Some(CastOrConversionExt.toMethodName)
    case `toOrNullMethodName` => Some(CastOrConversionExt.toOrNullMethodName)
    case _                    => None
  }

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] = {
    if (conversion.appliesToConversion(clazz)) {
      definitionsByName
    } else {
      Map.empty
    }
  }

  protected def definitions(): List[MethodDefinition] = {
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

  def apply(conversion: Conversion[_]): ConversionExt = new ConversionExt(conversion)

}
