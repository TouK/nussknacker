package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition}
import pl.touk.nussknacker.engine.extension.ExtensionMethod.{NoArg, SingleArg}
import pl.touk.nussknacker.engine.spel.internal.ConversionHandler

import java.util
import java.util.{List => JList}

class ArrayWrapper(target: Any) extends util.AbstractList[Object] {
  private val asList                   = ConversionHandler.convertArrayToList(target)
  override def get(index: Int): AnyRef = asList.get(index)
  override def size(): Int             = asList.size()
}

object ArrayExt extends ExtensionMethodsDefinition {

  private val methodRegistry: Map[String, ExtensionMethod[_]] = Map(
    "get"         -> SingleArg((target, arg: Int) => new ArrayWrapper(target).get(arg)),
    "size"        -> NoArg(target => new ArrayWrapper(target).size()),
    "lastIndexOf" -> SingleArg((target, arg: Any) => new ArrayWrapper(target).lastIndexOf(arg)),
    "contains"    -> SingleArg((target, arg: Any) => new ArrayWrapper(target).contains(arg)),
    "indexOf"     -> SingleArg((target, arg: Any) => new ArrayWrapper(target).indexOf(arg)),
    "containsAll" -> SingleArg((target, arg: util.Collection[_]) => new ArrayWrapper(target).containsAll(arg)),
    "isEmpty"     -> NoArg(target => new ArrayWrapper(target).isEmpty),
    "empty"       -> NoArg(target => new ArrayWrapper(target).isEmpty),
  )

  override def findMethod(
      clazz: Class[_],
      methodName: String,
      argsSize: Int,
      set: ClassDefinitionSet
  ): Option[ExtensionMethod[_]] =
    if (appliesToClassInRuntime(clazz))
      methodRegistry.get(methodName).filter(_.argsSize == argsSize)
    else
      None

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] =
    if (appliesToClassInRuntime(clazz)) {
      set
        .get(classOf[JList[_]])
        .map(_.methods)
        .getOrElse(Map.empty)
    } else {
      Map.empty
    }

  private def appliesToClassInRuntime(clazz: Class[_]): Boolean = clazz.isArray

}
