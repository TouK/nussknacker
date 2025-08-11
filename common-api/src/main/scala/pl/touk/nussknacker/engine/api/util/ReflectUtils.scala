package pl.touk.nussknacker.engine.api.util

import java.lang.reflect.{InvocationTargetException, Method, Proxy => JavaProxy}
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

  // We don't use T <: Enum[T] because it won't be possible to pass Enum[_]
  def javaEnumValueOf[T <: Enum[_]](clazz: Class[T], stringValue: String): T = {
    if (!clazz.isEnum)
      throw new IllegalArgumentException(s"Class ${clazz.getName} is not an Enum")

    val valueOfMethod = clazz.getMethod("valueOf", classOf[String])
    try {
      valueOfMethod.invoke(null, stringValue).asInstanceOf[T]
    } catch {
      case invocationTargetException: InvocationTargetException =>
        throw invocationTargetException.getTargetException
    }
  }

  def javaEnumNames(clazz: Class[_]): Array[String] = {
    if (!clazz.isEnum)
      throw new IllegalArgumentException(s"Class ${clazz.getName} is not an Enum")

    clazz.asInstanceOf[Class[Enum[_]]].getEnumConstants.map(_.name())
  }

}
