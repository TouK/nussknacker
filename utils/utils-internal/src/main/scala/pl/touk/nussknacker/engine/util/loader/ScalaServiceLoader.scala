package pl.touk.nussknacker.engine.util.loader

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.NamedServiceProvider
import pl.touk.nussknacker.engine.util.Implicits.RichStringList
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}

import java.net.URLClassLoader
import java.util.ServiceLoader
import scala.reflect.{ClassTag, classTag}

object ScalaServiceLoader extends LazyLogging {
  import scala.jdk.CollectionConverters._

  def loadClass[T: ClassTag](classLoader: ClassLoader)(createDefault: => T): T =
    chooseClass[T](createDefault, load[T](classLoader))

  def chooseClass[T](createDefault: => T, loaded: List[T]): T = {
    Multiplicity(loaded) match {
      case One(only) => only
      case Empty()   => createDefault
      case _ => throw new IllegalArgumentException(s"Error at loading class - default: $createDefault, loaded: $loaded")
    }
  }

  def loadNamed[T <: NamedServiceProvider: ClassTag](
      name: String,
      classLoader: ClassLoader = Thread.currentThread().getContextClassLoader
  ): T = {
    val available = load[T](classLoader)
    val className = implicitly[ClassTag[T]].runtimeClass.getName
    Multiplicity(available.filter(_.name == name)) match {
      case Empty() =>
        throw new IllegalArgumentException(
          s"Failed to find $className with name '$name', available names: ${available.map(_.name).distinct.mkString(", ")}"
        )
      case One(instance) => instance
      case Many(more) =>
        throw new IllegalArgumentException(
          s"More than one $className with name '$name' found: ${more.map(_.getClass).mkString(", ")}"
        )
    }
  }

  def load[T: ClassTag](classLoader: ClassLoader): List[T] = {
    val interface: Class[T] = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    val loadedClasses = ServiceLoader
      .load(interface, classLoader)
      .asScala
      .toList
    logLoadedClassesAndClassloaders(interface, loadedClasses)
    loadedClasses
  }

  private def logLoadedClassesAndClassloaders(interface: Class[_], loadedClasses: List[_]): Unit = {
    logger.debug(
      loadedClasses
        .map { cl =>
          val classLoader = cl.getClass.getClassLoader match {
            case urlCL: URLClassLoader =>
              s"${urlCL.getURLs.map(_.toString).toList.mkCommaSeparatedStringWithPotentialEllipsis(10)})"
            case other =>
              s"${other.getName}"
          }
          s"\n  ${cl.getClass.getName} loaded with classloader $classLoader"
        }
        .mkString(s"Classes loaded for the ${interface.getName} interface: ", ", ", "")
    )
  }

}
