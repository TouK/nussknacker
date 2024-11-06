package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition}
import pl.touk.nussknacker.engine.spel.internal.ConversionHandler

import java.util
import java.util.{List => JList}

class ArrayWrapper(target: Any) extends util.AbstractList[Object] {
  private val asList                                       = ConversionHandler.convertArrayToList(target)
  override def get(index: Int): AnyRef                     = asList.get(index)
  override def size(): Int                                 = asList.size()
  override def lastIndexOf(o: Any): Int                    = super.lastIndexOf(o)
  override def contains(o: Any): Boolean                   = super.contains(o)
  override def indexOf(o: Any): Int                        = super.indexOf(o)
  override def containsAll(c: util.Collection[_]): Boolean = super.containsAll(c)
  override def isEmpty: Boolean                            = super.isEmpty
  def empty: Boolean                                       = super.isEmpty
}

class ArrayExt extends ExtensionMethodHandler {

  override val methodRegistry: Map[String, ExtensionMethod] = Map(
    "get" -> new ExtensionMethod {
      override val argsSize: Int = 1
      override def invoke(target: Any, args: Object*): Any =
        new ArrayWrapper(target).get(args.head.asInstanceOf[Integer])
    },
    "size" -> ((target: Any, _) => new ArrayWrapper(target).size()),
    "lastIndexOf" -> new ExtensionMethod {
      override val argsSize: Int = 1
      override def invoke(target: Any, args: Object*): Any =
        new ArrayWrapper(target).lastIndexOf(args.head.asInstanceOf[Integer])
    },
    "contains" -> new ExtensionMethod {
      override val argsSize: Int = 1
      override def invoke(target: Any, args: Object*): Any =
        new ArrayWrapper(target).contains(args.head)
    },
    "indexOf" -> new ExtensionMethod {
      override val argsSize: Int = 1
      override def invoke(target: Any, args: Object*): Any =
        new ArrayWrapper(target).indexOf(args.head)
    },
    "containsAll" -> new ExtensionMethod {
      override val argsSize: Int = 1
      override def invoke(target: Any, args: Object*): Any =
        new ArrayWrapper(target).containsAll(args.head.asInstanceOf[util.Collection[_]])
    },
    "isEmpty" -> ((target: Any, _) => new ArrayWrapper(target).isEmpty()),
    "empty"   -> ((target: Any, _) => new ArrayWrapper(target).isEmpty()),
  )

}

object ArrayExt extends ExtensionMethodsDefinition {

  override def createHandler(set: ClassDefinitionSet): ExtensionMethodHandler = new ArrayExt

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
