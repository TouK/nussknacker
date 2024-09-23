package pl.touk.nussknacker.engine.extension

import java.lang.reflect.Method

object ExtensionMethods {

  private val declarationsWithImplementations = Map[Class[_], Constructor](
    classOf[Cast] -> new CastConstructor,
  )

  val registry: Set[Class[_]] = declarationsWithImplementations.keySet

  def applies(clazz: Class[_]): Boolean = registry.contains(clazz)

  def invoke(method: Method, target: Any, arguments: Array[Object], classLoader: ClassLoader): Any =
    declarationsWithImplementations
      .get(method.getDeclaringClass)
      .map { case constructor: CastConstructor =>
        constructor.apply(target, classLoader)
      }
      .map(impl => method.invoke(impl, arguments: _*))
      .getOrElse {
        throw new IllegalArgumentException(s"Extension method: ${method.getName} is not implemented")
      }

  sealed trait Constructor

  class CastConstructor extends Constructor {
    def apply(target: Any, classLoader: ClassLoader): Cast = new CastImpl(target, classLoader)
  }

}
