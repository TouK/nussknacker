package pl.touk.nussknacker.ui.api

import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.engine.util.ResourceLoader
import pl.touk.nussknacker.ui.extrajs.ExtraScriptsListingPreparer

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.util.Try

class WebResources(publicPath: String) extends Directives with LazyLogging {

  // see config.js comment
  private lazy val mainContentFile = {
    val mainPath = "/web/static/main.html"
    val data     = Try(ResourceLoader.load(mainPath))
    val content = data.toOption.getOrElse {
      logger.error(
        s"Failed to find $mainPath - probably frontend resources are not packaged in jar. Frontend won't work properly!"
      )
      ""
    }

    val extraScripts = {
      val extraStaticRoot  = Path.of("static", "extra")
      val webResourcesRoot = Path.of(Option(publicPath).filterNot(_.isBlank).getOrElse("/")).resolve(extraStaticRoot)
      new ExtraScriptsListingPreparer(
        getClass.getClassLoader,
        Path.of("web").resolve(extraStaticRoot),
        webResourcesRoot
      ).scriptsListing
    }

    val withPublicPathSubstituted = content
      .replace("</body>", s"\n$extraScripts\n</body>")
      .replace("__publicPath__", publicPath)

    val tempMainContentFile = Files.createTempFile("nussknacker", "main.html").toFile
    tempMainContentFile.deleteOnExit()
    FileUtils.writeStringToFile(tempMainContentFile, withPublicPathSubstituted, StandardCharsets.UTF_8)
    tempMainContentFile
  }

  val route: Route = handleAssets("submodules") ~ handleAssets("static") ~ get {
    // main.html instead of index.html to not interfere with flink's static resources...
    getFromFile(mainContentFile) // return UI page by default to make links work
  }

  private def handleAssets(webSubfolder: String) = {
    pathPrefix(webSubfolder) {
      get {
        encodeResponse {
          extractRequest { matched =>
            logger.debug(s"Try to get static data from $webSubfolder for: ${matched.uri.path}.")
            getFromResourceDirectory(s"web/$webSubfolder")
          }
        }
      }
    }
  }

}
