package pl.touk.nussknacker.engine.util.loader

import java.io.File
import java.net.{URL, URLClassLoader}

import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator

import scala.util.control.NonFatal

class JarClassLoader private(val classLoader: ClassLoader, val jarUrl: URL) {

  def loadClass(className: String): Class[_] =
    classLoader.loadClass(className)

  def instantiate[T](className: String): T =
    loadClass(className).newInstance().asInstanceOf[T]

  def createProcessConfigCreator(className: String): ProcessConfigCreator = try {
    instantiate[ProcessConfigCreator](className)
  } catch {
    case NonFatal(e) => throw new IllegalArgumentException("Failed to instantiate ProcessConfigCreator. " +
      "Please make sure that model jar is configured correctly (pay special attention to jarPath and processConfigCreatorClass settings)", e)
  }

  lazy val file: File =
    new File(jarUrl.toURI)
}

object JarClassLoader {
  def apply(jarUrl: URL): JarClassLoader =
    new JarClassLoader(classLoader(jarUrl), jarUrl)

  def apply(file: File): JarClassLoader =
    apply(file.toURI.toURL)

  def apply(path: String): JarClassLoader =
    apply(url(path))

  private def url(path: String) =
    new File(path).toURI.toURL

  def classLoader(jarUrl: URL): URLClassLoader =
    new URLClassLoader(Array(jarUrl), getClass.getClassLoader)

}
