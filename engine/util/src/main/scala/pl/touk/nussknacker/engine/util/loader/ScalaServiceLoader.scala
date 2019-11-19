package pl.touk.nussknacker.engine.util.loader

import java.util.ServiceLoader

import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Multiplicity, One}

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
}

