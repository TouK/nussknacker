package pl.touk.nussknacker.engine.management

import java.io.{File, FileOutputStream}
import java.net.URL
import java.nio.file.Files
import java.util.jar.{JarEntry, JarInputStream, JarOutputStream}

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.{FileUtils, IOUtils}
import pl.touk.nussknacker.engine.ModelData

class FlinkModelJar extends LazyLogging {

  //TODO: handle multiple models?
  private var modelFile: Option[File] = None

  //we want to have *different* file names for *different* model data (e.g. after rebuild etc.)
  //currently we just generate random file names
  def buildJobJar(modelData: ModelData): File = synchronized {
    modelFile match {
      case Some(file) => file
      case None =>
        val newFile = prepareModelFile(modelData)
        modelFile = Some(newFile)
        newFile
    }
  }

  protected def generateModelFileName(modelData: ModelData): File = {
    //currently we want to have one such file for one nussknacker execution
    val tempFile = Files.createTempFile("tempModelJar", ".jar").toFile
    tempFile.deleteOnExit()
    tempFile
  }

  private def prepareModelFile(modelData: ModelData): File = {
    val tempFile = generateModelFileName(modelData)

    //It seems that Flink leaks file descriptors (ZipEntries) when using file upload + jars embedded in lib folder...
    //fixed in https://issues.apache.org/jira/browse/FLINK-21164
    modelData.modelClassLoader.urls match {
      case single :: Nil if single.getPath.endsWith(".jar") =>
        logger.info("Single jar file detected, using directly to upload to Flink")
        FileUtils.copyInputStreamToFile(single.openStream(), tempFile)
      case other =>
        logger.warn("Multiple URL detected in classpath, embedding in lib folder, this can lead to memory leaks...")
        copyEntriesToLib(tempFile, other)
    }
    tempFile
  }

  private def copyEntriesToLib(tempFile: File, other: List[URL]): Unit = {
    val output = new FileOutputStream(tempFile)
    val jarOutput = new JarOutputStream(output)
    try {
      other.foreach(putToJar(jarOutput))
    } finally {
      jarOutput.close()
    }
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
