package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging

import java.io.File
import java.nio.file.Path
import scala.io.Source

class ExtraScriptsListingPreparer(classLoader: ClassLoader,
                                  extraScriptsPath: Path,
                                  webResourcesRoot: Path) extends LazyLogging {
  def scriptsListing: String = {
    webResourcesListing.map(resourcePath => s"""<script src="$resourcePath"></script>""").mkString("\n")
  }

  private[api] def webResourcesListing: Seq[String] = {
    val matchingFiles = readListingFile orElse listDirector getOrElse {
      logger.debug(s"Directory with extra scripts: $extraScriptsPath is not available from classpath or is inside a jar")
      Seq.empty[String]
    }
    matchingFiles
      .sorted
      .map(name => webResourcesRoot.resolve(name).toString)
  }

  private def readListingFile: Option[Seq[String]] = {
    val listingFilePath = extraScriptsPath.resolve("scripts.lst")
    Option(classLoader.getResourceAsStream(listingFilePath.toString))
      .map(Source.fromInputStream)
      .map(_.getLines().toSeq)
      .map { seq =>
        logger.debug(s"Extra scripts listed in the listing file $listingFilePath: ${seq.mkString(", ")}")
        seq
      }
  }

  private def listDirector: Option[Seq[String]] = {
    for {
      existingExtraScriptsRoot <- Option(classLoader.getResource(extraScriptsPath.toString))
      extraScriptsRootFile = new File(existingExtraScriptsRoot.getFile)
      matchingFiles <- Option(extraScriptsRootFile.listFiles((_, fileName) => fileName.endsWith(".js")))
      fileNamesSeq = matchingFiles.toIndexedSeq.map(_.getName)
    } yield {
      logger.debug(s"Extra scripts listed in the directory $extraScriptsPath: ${matchingFiles.mkString(", ")}")
      fileNamesSeq
    }
  }

}
