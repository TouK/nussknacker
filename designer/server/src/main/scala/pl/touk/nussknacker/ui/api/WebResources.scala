package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.engine.util.ResourceLoader

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.util.Try

class WebResources(publicPath: String) extends Directives with LazyLogging {

  //see config.js comment
  private lazy val mainContentFile = {
    val tempMainContentFile = Files.createTempFile("nussknacker", "main.html").toFile
    tempMainContentFile.deleteOnExit()
    val staticRoot = "/web/static"
    val data = Try(ResourceLoader.load(Path.of(staticRoot, "/main.html").toString))

    val content = data.toOption.getOrElse {
      logger.error("Failed to find web/static/main.html - probably frontend resources are not packaged in jar. Frontend won't work properly!")
      ""
    }

    val extraScriptsDir = "/extra"
    val path = getClass.getResource(Path.of(staticRoot, extraScriptsDir).toString)
    val files = Option(new File(path.getFile).list((_, name) => name.endsWith(".js")))
    val extraScripts = files
      .getOrElse(Array.empty)
      .sorted
      .map(file => s"<script src=\"${Path.of(publicPath, extraScriptsDir, file)}\"></script>")
      .mkString("\n")

    val withPublicPathSubstituted = content
      .replace("</body>", s"<!--\n $extraScripts //-->\n</body>")
      .replace("__publicPath__", publicPath)

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
            logger.debug(s"Try to get static data from $webSubfolder for:  ${matched.uri.path}.")
            getFromResourceDirectory(s"web/$webSubfolder")
          }
        }
      }
    }
  }

}
