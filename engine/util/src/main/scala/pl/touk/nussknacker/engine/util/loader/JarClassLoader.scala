package pl.touk.nussknacker.engine.util.loader

import java.io.File
import java.net.{URL, URLClassLoader}
import java.util.ServiceLoader

import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator

class JarClassLoader private(val classLoader: ClassLoader, val jarUrl: URL) {

  lazy val file: File =
    new File(jarUrl.toURI)

  def createProcessConfigCreator: ProcessConfigCreator = {
    ProcessConfigCreatorServiceLoader.createProcessConfigCreator(classLoader)
  }
}

object JarClassLoader {

  def apply(path: String): JarClassLoader =
    JarClassLoader(url(path))

  def apply(jarUrl: URL): JarClassLoader =
    new JarClassLoader(classLoader(jarUrl), jarUrl)

  private def classLoader(jarUrl: URL): URLClassLoader =
    new URLClassLoader(Array(jarUrl), this.getClass.getClassLoader)

  private def url(path: String) =
    new File(path).toURI.toURL


}
