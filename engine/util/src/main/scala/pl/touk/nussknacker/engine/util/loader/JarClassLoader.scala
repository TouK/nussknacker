package pl.touk.nussknacker.engine.util.loader

import java.io.File
import java.net.{URL, URLClassLoader}
import java.util.ServiceLoader

import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

class JarClassLoader private(val classLoader: ClassLoader, file: Option[File]) {

  def createProcessConfigCreator: ProcessConfigCreator = {
    ProcessConfigCreatorServiceLoader.createProcessConfigCreator(classLoader)
  }

  def loadServices[T: ClassTag] : List[T] = {
    ServiceLoader.load(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]], classLoader)
      .iterator().asScala.toList
  }

  def urls: List[URL] = file.toList.map(_.toURI.toURL)

  def tryToGetFile : File = file.getOrElse(throw new IllegalArgumentException("No file provided"))

}

object JarClassLoader {

  //for e.g. testing in process module
  val empty = new JarClassLoader(getClass.getClassLoader, None)

  def apply(path: String): JarClassLoader =
    JarClassLoader(new File(path))

  private def apply(file: File): JarClassLoader =
    new JarClassLoader(classLoader(file), Some(file))

  private def classLoader(file: File): URLClassLoader = {
    val jarUrl = file.toURI.toURL
    new URLClassLoader(Array(jarUrl), this.getClass.getClassLoader)
  }

}
