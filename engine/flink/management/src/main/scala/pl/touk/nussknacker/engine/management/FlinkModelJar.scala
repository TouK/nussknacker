package pl.touk.nussknacker.engine.management

import java.io.{File, FileOutputStream}
import java.net.URL
import java.nio.file.Files
import java.util.jar.{JarEntry, JarOutputStream}

import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.engine.ModelData

class FlinkModelJar {

  //TODO: handle multiple models?
  private var modelFile: Option[File] = None

  def buildJobJar(modelData: ModelData): File = synchronized {
    modelFile match {
      case Some(file) => file
      case None =>
        val newFile = prepareModelFile(modelData)
        modelFile = Some(newFile)
        newFile
    }
  }

  private def prepareModelFile(modelData: ModelData): File = {
    //currently we want to have one such file for one nussknacker execution
    val tempFile = Files.createTempFile("tempModelJar", ".jar").toFile
    tempFile.deleteOnExit()

    val output = new FileOutputStream(tempFile)
    val jarOutput = new JarOutputStream(output)

    modelData.modelClassLoader.urls.foreach(putToJar(jarOutput))
    jarOutput.close()

    tempFile
  }

  private def putToJar(jarOutputStream: JarOutputStream)(url: URL) = {
    val entry = new JarEntry(s"lib/${url.getPath.replaceAll("/", "_")}")
    jarOutputStream.putNextEntry(entry)
    val stream = url.openStream()
    try {
      IOUtils.copy(stream, jarOutputStream)
    } finally {
      stream.close()
    }
  }

}
