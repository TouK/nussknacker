package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging

import java.io.File

class ExtraScriptsListingPreparer(classLoader: ClassLoader,
                                  extraScriptsPath: String) extends LazyLogging {

  private[api] lazy val webResourcesListing: Seq[String] = {
    val matchingFilesForExistingDirectory = for {
      existingExtraScriptsRoot <- Option(classLoader.getResource(extraScriptsPath))
        // for resources located as a files (instead of in resources), first slash should be omitted
        .orElse(Option(classLoader.getResource(extraScriptsPath.replaceFirst("^/", ""))))
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
        .map(_.getName))
    } yield {
      logger.debug(s"Extra scripts listed in the directory $extraScriptsPath: ${matchingFiles.mkString(", ")}")
      matchingFiles
    }
    val matchingFiles = matchingFilesForExistingDirectory.getOrElse {
      logger.debug(s"Directory with extra scripts: $extraScriptsPath is not available from classpath")
      Array.empty
    }
    matchingFiles.sorted.map(name => s"$extraScriptsPath/$name")
  }

  lazy val scriptsListing: String = {
    webResourcesListing.map(resourcePath => s"""<script src="$resourcePath"></script>""").mkString("\n")
  }


}
