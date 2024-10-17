package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.extension.ListExt.{key, value}
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassExtensions

import java.util.{HashMap => JHashMap, List => JList, Map => JMap}

class ListExt(target: Any) {

  def toMap[K, V](): JMap[K, V] = {
    val map = new JHashMap[K, V]()
    target
      .asInstanceOf[JList[JMap[String, Any]]]
      .forEach(e => map.put(e.get(key).asInstanceOf[K], e.get(value).asInstanceOf[V]))
    map
  }

}

object ListExt
    extends ExtensionMethodsFactory
    with ExtensionMethodsDefinitionsExtractor
    with ExtensionRuntimeApplicable {

  private val listClass = classOf[JList[_]]

  private val definitions = List(
    FunctionalMethodDefinition(
      typeFunction = (invocationTarget, _) => typeFunction(invocationTarget),
      signature = MethodTypeInfo(
        noVarArgs = Nil,
        varArg = None,
        result = Unknown
      ),
      name = "toMap",
      description = Option("Convert a list to a map")
    )
  ).groupBy(_.name)

  private val key   = "key"
  private val value = "value"

  override def create(target: Any, classLoader: ClassLoader, classesBySimpleName: Map[String, Class[_]]): Any =
    new ListExt(target)

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] =
    if (clazz.isAOrChildOf(listClass)) definitions
    else Map.empty

  override def applies(clazz: Class[_]): Boolean =
    clazz.isAOrChildOf(listClass)

  private def typeFunction(invocationTarget: TypingResult): ValidatedNel[GenericFunctionTypingError, TypingResult] =
    invocationTarget.withoutValue match {
      case TypedClass(_, List(TypedObjectTypingResult(fields, _, _)))
          if fields.contains(key) && fields.contains(value) =>
        val params = List(fields.get(key), fields.get(value)).flatten
        Typed.genericTypeClass[JMap[_, _]](params).validNel
      case TypedClass(_, List(TypedObjectTypingResult(_, _, _))) =>
        GenericFunctionTypingError.OtherError("List element must contain 'key' and 'value' fields").invalidNel
      case _ => GenericFunctionTypingError.ArgumentTypeError.invalidNel
    }

}
