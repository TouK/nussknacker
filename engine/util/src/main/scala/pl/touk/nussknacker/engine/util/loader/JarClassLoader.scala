package pl.touk.nussknacker.engine.util.loader

import java.io.File
import java.net.{URL, URLClassLoader}

class JarClassLoader private(val classLoader: ClassLoader, file: Option[File]) {
  def urls: List[URL] = file.toList.map(_.toURI.toURL)

  def tryToGetFile: File = file.getOrElse(throw new IllegalArgumentException("No file provided"))
}

object JarClassLoader {
  //for e.g. testing in process module
  val empty = JarClassLoader(getClass.getClassLoader, None)

  def apply(path: String): JarClassLoader =
    JarClassLoader(new File(path))

  private def apply(file: File): JarClassLoader =
    JarClassLoader(classLoader(file), Some(file))

  def apply(classLoader: ClassLoader, file: Option[File]): JarClassLoader = {
    new JarClassLoader(classLoader, file)
  }

  private def classLoader(file: File): URLClassLoader = {
    val jarUrl = file.toURI.toURL
    new URLClassLoader(Array(jarUrl), this.getClass.getClassLoader)
  }

}


