package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassExtensions

import java.util
import java.util.{List => JList, Set => JSet}

class SetExt(target: Any) {
  def toList[T](): JList[T] =
    new util.ArrayList[T](target.asInstanceOf[JSet[T]])
}

object SetExt
    extends ExtensionMethodsFactory
    with ExtensionMethodsDefinitionsExtractor
    with ExtensionRuntimeApplicable {

  private val setClass = classOf[JSet[_]]

  private val definitions = List(
    FunctionalMethodDefinition(
      typeFunction = (invocationTarget, _) => typeFunction(invocationTarget),
      signature = MethodTypeInfo(
        noVarArgs = Nil,
        varArg = None,
        result = Unknown
      ),
      name = "toList",
      description = Option("Convert a set to a list")
    )
  ).groupBy(_.name)

  private def typeFunction(invocationTarget: TypingResult): ValidatedNel[GenericFunctionTypingError, TypingResult] = {
    invocationTarget.withoutValue match {
      case TypedClass(_, params) => Typed.genericTypeClass[JList[_]](params).validNel
      case _                     => GenericFunctionTypingError.ArgumentTypeError.invalidNel
    }
  }

  override def create(target: Any, classLoader: ClassLoader, classesBySimpleName: Map[String, Class[_]]): Any =
    new SetExt(target)

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] =
    if (clazz.isAOrChildOf(setClass)) definitions
    else Map.empty

  override def applies(clazz: Class[_]): Boolean =
    clazz.isAOrChildOf(setClass)
}
