package pl.touk.nussknacker.ui.api

import java.io.File

class ExtraScriptsListingPreparer(classLoader: ClassLoader,
                                  extraScriptsPath: String) {

  private[api] lazy val webResourcesListing: Seq[String] = {
    val extraScriptsRootResource = classLoader.getResource(extraScriptsPath)
    val matchingFiles = (for {
      existingExtraScriptsRoot <- Option(extraScriptsRootResource)
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
        .listFiles((_, fileName) => fileName.endsWith(".js")))
    } yield matchingFiles).getOrElse(Array.empty)
    matchingFiles.sorted.map(f => s"/$extraScriptsPath/${f.getName}")
  }

  lazy val scriptsListing: String = {
    webResourcesListing.map(resourcePath => s"""<script src="$resourcePath"></script>""").mkString("\n")
  }


}
