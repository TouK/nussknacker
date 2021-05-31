package pl.touk.nussknacker.test

import java.net.URLClassLoader
import java.nio.file.{Files, Path}
import scala.collection.mutable.ArrayBuffer

object ClassLoaderWithServices {

  /*
    Sometimes we want to test ServiceLoader mechanisms, adding definitions to test classpath can by tricky as it's global for all tests
    This method takes list of pairs (interface, implementation) and creates classloader with appropriate service definitions
   */
  def withCustomServices[T](services: List[(Class[_], Class[_])], parent: ClassLoader = Thread.currentThread().getContextClassLoader)(action: ClassLoader => T): T = {

    val tempDir = Files.createTempDirectory("classLoaderServices")
    val loader = new URLClassLoader(Array(tempDir.toUri.toURL), parent)

    val filesToDelete = new ArrayBuffer[Path]
    try {
      val servicesDir = tempDir.resolve("META-INF/services")
      servicesDir.toFile.mkdirs()
      services.foreach { case (interface, implementation) =>
        val file = servicesDir.resolve(interface.getName)
        Files.write(file, implementation.getName.getBytes)
        filesToDelete.append(file)
      }
      filesToDelete.append(servicesDir, servicesDir.getParent, tempDir)
      action(loader)
    } finally {
      loader.close()
      filesToDelete.foreach(Files.delete)
    }


  }

}

