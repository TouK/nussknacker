package pl.touk.nussknacker.ui.extrajs

import com.typesafe.scalalogging.LazyLogging

import java.io.File
import java.nio.file.Path
import scala.io.Source

// The purpose of this listing is to be possible to dynamically (without changing application image)
// add some java scripts to our main.html. Example usage:
//
// docker run -it --network host -e CLASSPATH="/opt/nussknacker/lib/*:/opt/nussknacker/extra-resources"
// -v ./extrajs:/opt/nussknacker/extra-resources/web/static/extra touk/nussknacker:latest
//
// After this, all *.js in the extrajs directory will be injected into main.html in the lexicographic order. Notice that if you want to locally
// develop with ./buildServer.sh and ./runServer.sh and place js in src/main/resource/web/static/extra, you should add
// scripts.lst listing file next to them, because resources inside jars can't be listed
class ExtraScriptsListingPreparer(classLoader: ClassLoader, extraScriptsPath: Path, webResourcesRoot: Path)
    extends LazyLogging {

  private val listingFilePath = extraScriptsPath.resolve("scripts.lst")

  def scriptsListing: String = {
    webResourcesListing.map(resourcePath => s"""<script defer src="$resourcePath"></script>""").mkString("\n")
  }

  private[extrajs] def webResourcesListing: Seq[String] = {
    val matchingFiles = readListingFile orElse listDirector getOrElse {
      logger.debug(s"Neither listing file $listingFilePath nor listable directory $extraScriptsPath are available")
      Seq.empty[String]
    }
    matchingFiles.sorted
      .map(name => webResourcesRoot.resolve(name).toString)
  }

  private def readListingFile: Option[Seq[String]] = {
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
