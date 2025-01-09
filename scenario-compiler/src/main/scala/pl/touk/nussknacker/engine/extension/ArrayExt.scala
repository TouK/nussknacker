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

  override def findMethod(
      clazz: Class[_],
      methodName: String,
      argsSize: Int,
      set: ClassDefinitionSet
  ): Option[ExtensionMethod[_]] =
    if (appliesToClassInRuntime(clazz)) {
      findMethod(methodName, clazz.getComponentType).filter(_.argsSize == argsSize)
    } else {
      None
    }

  private def findMethod(methodName: String, componentType: Class[_]): Option[ExtensionMethod[_]] = methodName match {
    case "get" =>
      val method = new ExtensionMethod[AnyRef] {
        override val argsSize: Int = 1
        override def invoke(target: Any, args: Object*): AnyRef =
          new ArrayWrapper(target).get(args.head.asInstanceOf[Int])
        override def returnType: Class[_] = componentType
      }
      Some(method)
    case "size"        => Some(NoArg(target => new ArrayWrapper(target).size()))
    case "lastIndexOf" => Some(SingleArg((target, arg: Any) => new ArrayWrapper(target).lastIndexOf(arg)))
    case "contains"    => Some(SingleArg((target, arg: Any) => new ArrayWrapper(target).contains(arg)))
    case "indexOf"     => Some(SingleArg((target, arg: Any) => new ArrayWrapper(target).indexOf(arg)))
    case "containsAll" =>
      Some(SingleArg((target, arg: util.Collection[_]) => new ArrayWrapper(target).containsAll(arg)))
    case "isEmpty" => Some(NoArg(target => new ArrayWrapper(target).isEmpty))
    case "empty"   => Some(NoArg(target => new ArrayWrapper(target).isEmpty))
    case _         => None
  }

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
