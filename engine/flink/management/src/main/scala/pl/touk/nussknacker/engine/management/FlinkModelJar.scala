package pl.touk.nussknacker.engine.management

import java.io.{File, FileOutputStream}
import java.net.URL
import java.nio.file.Files
import java.util.jar.{JarEntry, JarInputStream, JarOutputStream}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.{FileUtils, IOUtils}
import pl.touk.nussknacker.engine.ModelData

import scala.util.Using

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
    modelData.modelClassLoader.urls match {
      case single :: Nil if single.getPath.endsWith(".jar") =>
        logger.debug("Single jar file detected, using directly to upload to Flink")
        FileUtils.copyInputStreamToFile(single.openStream(), tempFile)
      case other =>
        logger.info("Multiple URL detected in classpath, embedding in lib folder")
        copyEntriesToLib(tempFile, other)
    }
    tempFile
  }

  private def copyEntriesToLib(tempFile: File, other: List[URL]): Unit = {
    val output = new FileOutputStream(tempFile)
    Using.resource(new JarOutputStream(output)) { jarOutput =>
      other.foreach(putToJar(jarOutput))
    }
  }

  private def putToJar(jarOutputStream: JarOutputStream)(url: URL) = {
    val entry = new JarEntry(s"lib/${url.getPath.replaceAll("/", "_")}")
    jarOutputStream.putNextEntry(entry)
    Using.resource(url.openStream()) { stream =>
      IOUtils.copy(stream, jarOutputStream)
    }
  }

}
