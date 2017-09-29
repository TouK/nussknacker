package pl.touk.nussknacker.engine.util.loader

import java.util.ServiceLoader

import pl.touk.nussknacker.engine.util.multiplicity.Multiplicity

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

object SingleServiceLoader {
  def load[T](classLoader: ClassLoader)(implicit classTag: ClassTag[T]): Multiplicity[T] = {
    val claz: Class[T] = toClass(classTag)
    val services = ServiceLoader
      .load(claz, classLoader)
      .iterator()
      .asScala
      .toList
    Multiplicity(services)
  }

  private def toClass[T](implicit classTag: ClassTag[T]) = {
    classTag.runtimeClass.asInstanceOf[Class[T]]
  }

}

