package pl.touk.nussknacker.engine.api.typed

import org.apache.commons.lang3.ClassUtils

import scala.reflect.ClassTag

object ClazzRef {

  def unknown: ClazzRef = ClazzRef[Any]

  def apply[T: ClassTag]: ClazzRef = {
    ClazzRef(implicitly[ClassTag[T]].runtimeClass.getName)
  }

  def apply(clazz: Class[_]): ClazzRef = {
    ClazzRef(clazz.getName)
  }
}

case class ClazzRef(refClazzName: String) {
  def toClass(classLoader: ClassLoader): Class[_] = ClassUtils.getClass(classLoader, refClazzName)
}