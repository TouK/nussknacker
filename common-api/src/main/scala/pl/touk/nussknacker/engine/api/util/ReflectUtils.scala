package pl.touk.nussknacker.engine.api.util

import java.lang.reflect.{Method, Proxy => JavaProxy}
import scala.reflect.{classTag, ClassTag}

object ReflectUtils {

  def simpleNameWithoutSuffix(clazz: Class[_]): String = {
    clazz.getSimpleName match {
      case ""   => "(anonymous class)"
      case name => name.stripSuffix("$")
    }
  }

  def createADummyInstanceOf[T: NotNothing: ClassTag]: T = {
    val runtimeClass = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    JavaProxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array(runtimeClass),
        (_: Any, _: Method, _: Array[AnyRef]) =>
          throw new IllegalAccessException("This is a dummy implementation. It shouldn't be used!")
      )
      .asInstanceOf[T]
  }

}
