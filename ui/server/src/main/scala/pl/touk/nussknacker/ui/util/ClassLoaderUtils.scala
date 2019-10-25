package pl.touk.nussknacker.ui.util

import java.util.ServiceLoader

import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Multiplicity, One}

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

class ClassLoaderUtils[ClassToLoad: ClassTag](classLoader: ClassLoader) {
  import scala.collection.JavaConverters._

  def loadClass(createDefault: => ClassToLoad): ClassToLoad = loadClass(createDefault, loaded)

  def loadClass(createDefault: => ClassToLoad, loaded: List[ClassToLoad]): ClassToLoad =
    chooseClass(
      createDefault = createDefault,
      loaded = loaded
    ) match {
      case Success(searchClass) => searchClass
      case Failure(e) => throw e
    }

  private def chooseClass(createDefault: => ClassToLoad, loaded: List[ClassToLoad]): Try[ClassToLoad] = {
    Multiplicity(loaded) match {
      case One(only) => Success(only)
      case Empty() => Success(createDefault)
      case _ => Failure(new IllegalArgumentException(s"Error at loading class - default: $createDefault, loaded: $loaded"))
    }
  }

  private def loaded: List[ClassToLoad]
    = ServiceLoader.load(implicitly[ClassTag[ClassToLoad]].runtimeClass, classLoader).asScala.toList.map(_.asInstanceOf[ClassToLoad])
}

object ClassLoaderUtils {
  def apply[ClassToLoad: ClassTag](classLoader: ClassLoader): ClassLoaderUtils[ClassToLoad]
    = new ClassLoaderUtils[ClassToLoad](classLoader)
}
