package pl.touk.nussknacker.engine.extension

import java.lang.reflect.Method

object ExtensionMethods {

  private val declarationsWithImplementations = Map[Class[_], Any => Any](
    classOf[Cast] -> CastImpl.apply,
  )

  val registry: Set[Class[_]] = declarationsWithImplementations.keySet

  def applies(clazz: Class[_]): Boolean = registry.contains(clazz)

  def invoke(method: Method, target: Any, arguments: Array[Object]): Any =
    declarationsWithImplementations.get(method.getDeclaringClass) match {
      case Some(constructor) => method.invoke(constructor(target), arguments: _*)
      case None => throw new IllegalArgumentException(s"Extension method: ${method.getName} is not implemented")
    }

}
