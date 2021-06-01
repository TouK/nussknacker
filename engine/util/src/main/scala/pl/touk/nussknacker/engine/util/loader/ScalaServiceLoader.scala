package pl.touk.nussknacker.engine.util.loader

import pl.touk.nussknacker.engine.api.NamedServiceProvider

import java.util.ServiceLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}

import scala.reflect.ClassTag

object ScalaServiceLoader {
  import scala.collection.JavaConverters._

  def load[T](classLoader: ClassLoader)(implicit classTag: ClassTag[T]): List[T] = {
    val claz: Class[T] = toClass(classTag)
    ServiceLoader
      .load(claz, classLoader)
      .iterator()
      .asScala
      .toList
  }

  private def toClass[T](implicit classTag: ClassTag[T]): Class[T] = {
    classTag.runtimeClass.asInstanceOf[Class[T]]
  }

  def loadClass[T](classLoader: ClassLoader)(createDefault: => T)(implicit classTag: ClassTag[T]): T
    = chooseClass[T](createDefault, load[T](classLoader))

  def chooseClass[T](createDefault: => T, loaded: List[T]): T = {
    Multiplicity(loaded) match {
      case One(only) => only
      case Empty() => createDefault
      case _ => throw new IllegalArgumentException(s"Error at loading class - default: $createDefault, loaded: $loaded")
    }
  }

  def loadNamed[T<:NamedServiceProvider:ClassTag](name: String, classLoader: ClassLoader = Thread.currentThread().getContextClassLoader): T = {
    val available = load[T](classLoader)
    val className = implicitly[ClassTag[T]].runtimeClass.getName
    Multiplicity(available.filter(_.name == name)) match {
      case Empty() =>
        throw new IllegalArgumentException(s"Failed to find $className with name '$name', available names: ${available.map(_.name).mkString(", ")}")
      case One(instance) => instance
      case Many(more) =>
        throw new IllegalArgumentException(s"More than one $className with name '$name' found: ${more.map(_.getClass).mkString(", ")}")
    }
  }
}
