package pl.touk.nussknacker.engine.management.jobrunner

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.{FileUtils, IOUtils}

import java.io.{File, FileOutputStream}
import java.net.URL
import java.nio.file.Files
import java.util.jar.{JarEntry, JarOutputStream}
import scala.util.Using

class FlinkModelJarProvider(modelUrls: List[URL], includeDropwizardLibsImplicitly: Boolean = true) extends LazyLogging {

  private var modelFile: Option[File] = None

  // we want to have *different* file names for *different* model data (e.g. after rebuild etc.)
  // currently we just generate random file names
  def getJobJar(): File = synchronized {
    modelFile match {
      // check whether model file exists - temp files may disappear if application has been running for a long time
      case Some(file) if file.exists() => file
      case _ =>
        val newFile = prepareModelFile()
        modelFile = Some(newFile)
        newFile
    }
  }

  private def generateModelFileName(): File = {
    // currently we want to have one such file for one nussknacker execution
    val tempFile = Files.createTempFile("tempModelJar", ".jar").toFile
    tempFile.deleteOnExit()
    tempFile
  }

  private def additionalArtifactsToIncludeInJar(): List[URL] = {
    // Including dropwizard metrics library implicitly to preserve backward compatibility
    // with flink-metrics-dropwizard library bundled into flinkExecutor
    val additionalJarFiles = List(
      "flink-dropwizard-metrics-deps/flink-metrics-dropwizard.jar",
      "flink-dropwizard-metrics-deps/dropwizard-metrics-core.jar"
    )
    val additionalJarUrls = additionalJarFiles
      .map(filename => new File(filename))
      .filter(_.exists())
      .map(_.toURI.toURL)
    additionalJarUrls
  }

  private def prepareModelFile(): File = {
    val tempFile = generateModelFileName()
    val implicitlyIncludedArtifacts =
      if (includeDropwizardLibsImplicitly)
        additionalArtifactsToIncludeInJar().toSet -- modelUrls.toSet
      else Nil
    if (implicitlyIncludedArtifacts.nonEmpty) {
      logger.warn(s"""Including these files to model jar implicitly: [${implicitlyIncludedArtifacts.mkString(
          ", "
        )}] to keep backward compatibility""")
      logger.warn(
        s"""This implicit inclusion is only transitional, and you should add above files to classPath field in your configuration!!!"""
      )
    }
    implicitlyIncludedArtifacts.toList ++ modelUrls match {
      case single :: Nil if single.getPath.endsWith(".jar") =>
        logger.info(s"Single model URL detected, writing to ${tempFile.getAbsolutePath}")
        FileUtils.copyInputStreamToFile(single.openStream(), tempFile)
      case other =>
        logger.info(s"Multiple model URLs detected, embedding in lib folder and writing to ${tempFile.getAbsolutePath}")
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
