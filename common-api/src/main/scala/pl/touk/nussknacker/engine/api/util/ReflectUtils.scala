package pl.touk.nussknacker.engine.api.util

import java.lang.reflect.{Method, Proxy => JavaProxy}
import scala.reflect.{classTag, ClassTag}
import scala.util.Try

object ReflectUtils {

  def simpleNameWithoutSuffix(clazz: Class[_]): String = {
    clazz.getSimpleName match {
      case ""   => "(anonymous class)"
      case name => name.stripSuffix("$")
    }
  }

  def createADumbInstanceOf[T: NotNothing: ClassTag]: T = {
    val runtimeClass = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    Try(runtimeClass.getConstructor()).map(_.newInstance()).getOrElse {
      JavaProxy
        .newProxyInstance(
          getClass.getClassLoader,
          Array(runtimeClass),
          (_: Any, _: Method, _: Array[AnyRef]) =>
            throw new IllegalAccessException("This is a dumb implementation. It shouldn't be used!")
        )
        .asInstanceOf[T]
    }
  }

}
