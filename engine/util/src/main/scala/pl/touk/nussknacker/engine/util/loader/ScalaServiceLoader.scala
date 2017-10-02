package pl.touk.nussknacker.engine.util.loader

import java.util.ServiceLoader

import pl.touk.nussknacker.engine.util.multiplicity.Multiplicity

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

object ScalaServiceLoader {
  def load[T](classLoader: ClassLoader)(implicit classTag: ClassTag[T]): List[T] = {
    val claz: Class[T] = toClass(classTag)
    ServiceLoader
      .load(claz, classLoader)
      .iterator()
      .asScala
      .toList
  }

  private def toClass[T](implicit classTag: ClassTag[T]) = {
    classTag.runtimeClass.asInstanceOf[Class[T]]
  }

}

