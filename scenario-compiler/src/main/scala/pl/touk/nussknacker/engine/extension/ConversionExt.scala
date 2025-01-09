package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition, StaticMethodDefinition}
import pl.touk.nussknacker.engine.extension.ExtensionMethod.NoArg
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassExtensions

import java.lang.{Boolean => JBoolean}
import scala.reflect.ClassTag

class ConversionExt[T >: Null <: AnyRef: ClassTag](conversion: Conversion[T]) extends ExtensionMethodsDefinition {

  private lazy val definitionsByName = definitions().groupBy(_.name)
  private lazy val targetTypeName    = conversion.resultTypeClass.simpleName()

  private val canBeMethodName = s"${CastOrConversionExt.canBeMethodName}$targetTypeName"
  private val toMethodName    = s"${CastOrConversionExt.toMethodName}$targetTypeName"
  private val toOrNullMethodName =
    s"${CastOrConversionExt.toMethodName}${targetTypeName}${CastOrConversionExt.orNullSuffix}"

  // Convert methods should visible in runtime for every class because we allow invoke convert methods on an unknown
  // object in Typer, but in the runtime the same type could be known and that's why should add convert method to an
  // every class.
  override def findMethod(
      clazz: Class[_],
      methodName: String,
      argsSize: Int,
      set: ClassDefinitionSet
  ): Option[ExtensionMethod[_]] =
    for {
      mappedMethodName <- mapMethodName(methodName)
      underlyingMethod <- CastOrConversionExt.findMethod(clazz, mappedMethodName, 1, set)
      resultMethod = methodName match {
        case `canBeMethodName`    => canBeWrappedMethod(underlyingMethod)
        case `toMethodName`       => toWrappedMethod(underlyingMethod)
        case `toOrNullMethodName` => toWrappedMethod(underlyingMethod)
        // TODO_PAWEL what if not found?
      }
    } yield resultMethod

  private def canBeWrappedMethod(underlyingMethod: ExtensionMethod[_]): ExtensionMethod[_] = {
    new ExtensionMethod[Boolean] {
      override val argsSize: Int = 0
      override def invoke(target: Any, args: Object*): Boolean =
        underlyingMethod.invoke(target, targetTypeName).asInstanceOf[Boolean]
      override def returnType: Class[Boolean] = classOf[Boolean]
    }
  }

  private def toWrappedMethod(underlyingMethod: ExtensionMethod[_]): ExtensionMethod[_] = {
    new ExtensionMethod[T] {
      override val argsSize: Int = 0
      override def invoke(target: Any, args: Object*): T =
        underlyingMethod.invoke(target, targetTypeName).asInstanceOf[T]
      override def returnType: Class[T] = conversion.resultTypeClass
    }
  }

  private def mapMethodName(methodName: String): Option[String] = methodName match {
    case `canBeMethodName`    => Some(CastOrConversionExt.canBeMethodName)
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
    List(
      definition(
        Typed.typedClass[JBoolean],
        canBeMethodName,
        Some(s"Check whether the value can be convert to a $targetTypeName")
      ),
      definition(
        conversion.typingResult,
        toMethodName,
        Some(s"Convert the value to $targetTypeName or throw exception in case of failure")
      ),
      definition(
        conversion.typingResult,
        toOrNullMethodName,
        Some(s"Convert the value to $targetTypeName or null in case of failure")
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

  def apply[T >: Null <: AnyRef: ClassTag](conversion: Conversion[T]): ConversionExt[T] = new ConversionExt(conversion)

}
