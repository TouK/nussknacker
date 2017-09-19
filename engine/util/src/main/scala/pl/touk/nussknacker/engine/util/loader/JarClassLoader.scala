package pl.touk.nussknacker.engine.util.loader

import java.io.File
import java.net.URLClassLoader
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator

class JarClassLoader private(val classLoader: ClassLoader, val file: File) {

  def createProcessConfigCreator: ProcessConfigCreator = {
    ProcessConfigCreatorServiceLoader.createProcessConfigCreator(classLoader)
  }
}

object JarClassLoader {

  def apply(path: String): JarClassLoader =
    JarClassLoader(new File(path))

  def apply(file: File): JarClassLoader =
    new JarClassLoader(classLoader(file), file)

  private def classLoader(file: File): URLClassLoader = {
    val jarUrl = file.toURI.toURL
    new URLClassLoader(Array(jarUrl), this.getClass.getClassLoader)
  }

}
