package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition}
import pl.touk.nussknacker.engine.spel.internal.ConversionHandler

import java.util
import java.util.{List => JList}

class ArrayExt(target: Any) extends util.AbstractList[Object] with ExtensionMethodInvocationTarget {
  private val asList = ConversionHandler.convertArrayToList(target)

  override def invoke(methodName: String, arguments: Array[Object]): Any = methodName match {
    case "get"               => get(arguments(0).asInstanceOf[Integer])
    case "size"              => size()
    case "lastIndexOf"       => lastIndexOf(arguments(0))
    case "contains"          => contains(arguments(0))
    case "indexOf"           => indexOf(arguments(0))
    case "containsAll"       => containsAll(arguments(0).asInstanceOf[util.Collection[_]])
    case "isEmpty" | "empty" => isEmpty
    case _                   => throw new IllegalAccessException(s"Cannot find method with name: '$methodName'")
  }

  override def get(index: Int): AnyRef                     = asList.get(index)
  override def size(): Int                                 = asList.size()
  override def lastIndexOf(o: Any): Int                    = super.lastIndexOf(o)
  override def contains(o: Any): Boolean                   = super.contains(o)
  override def indexOf(o: Any): Int                        = super.indexOf(o)
  override def containsAll(c: util.Collection[_]): Boolean = super.containsAll(c)
  override def isEmpty: Boolean                            = super.isEmpty
  def empty: Boolean                                       = super.isEmpty

}

object ArrayExt extends ExtensionMethodsHandler[ArrayExt] {

  override val invocationTargetClass: Class[ArrayExt] = classOf[ArrayExt]

  override def createConverter(
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[ArrayExt] =
    (target: Any) => new ArrayExt(target)

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] =
    if (clazz.isArray) {
      set
        .get(classOf[JList[_]])
        .map(_.methods)
        .getOrElse(Map.empty)
    } else {
      Map.empty
    }

  override def appliesToClassInRuntime(clazz: Class[_]): Boolean = clazz.isArray
}
