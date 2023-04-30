package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging

import java.io.File
import java.nio.file.Path

class ExtraScriptsListingPreparer(classLoader: ClassLoader,
                                  extraScriptsPath: String,
                                  webResourcesRoot: Path) extends LazyLogging {
  def scriptsListing: String = {
    webResourcesListing.map(resourcePath => s"""<script src="$resourcePath"></script>""").mkString("\n")
  }

  private[api] def webResourcesListing: Seq[String] = {
    val matchingFilesForExistingDirectory = for {
      existingExtraScriptsRoot <- Option(classLoader.getResource(extraScriptsPath))
      extraScriptsRootFile = new File(existingExtraScriptsRoot.getFile)
      _ = {
        if (!extraScriptsRootFile.isDirectory) {
          throw new IllegalStateException(s"Extra scripts root: $extraScriptsRootFile is not a directory")
        }
        if (!extraScriptsRootFile.canRead) {
          throw new IllegalStateException(s"Can't read extra scripts root: $extraScriptsRootFile")
        }
      }
      matchingFiles <- Option(new File(existingExtraScriptsRoot.getFile)
        .listFiles((_, fileName) => fileName.endsWith(".js"))
        .toIndexedSeq
        .map(_.getName))
    } yield {
      logger.debug(s"Extra scripts listed in the directory $extraScriptsPath: ${matchingFiles.mkString(", ")}")
      matchingFiles
    }
    val matchingFiles = matchingFilesForExistingDirectory.getOrElse {
      logger.debug(s"Directory with extra scripts: $extraScriptsPath is not available from classpath")
      Seq.empty[String]
    }
    matchingFiles
      .sorted
      .map(name => webResourcesRoot.resolve(name).toString)
  }

}
