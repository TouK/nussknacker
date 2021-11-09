package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.{FileUtils, IOUtils}
import pl.touk.nussknacker.engine.util.cache.{CacheConfig, DefaultCache}

import java.io.File
import java.nio.file.Files

class WebResources(publicPath: String) extends Directives with LazyLogging {

  private val mainFilesCache = new DefaultCache[String, File](CacheConfig())

  private val defaultMainFilePath = "web/static/main.html"

  val route: Route =
    pathPrefix("submodules" / Segment /) { moduleName =>
      get {
        getFromResourceDirectory("web/submodules")
      }
    } ~ pathPrefix("static") {
      get {
        getFromResourceDirectory("web/static")
      }
    } ~ get {
      extractRequest { _ =>
        //main.html instead of index.html to not interfere with flink's static resources...
        getFromMainFile(defaultMainFilePath) //return UI page by default to make links work
      }
    }

    private def getFromMainFile(mainFilePath: String) = getFromFile(getFileContent(mainFilePath))

    //see config.js comment
    private def getFileContent(filePath: String): File = mainFilesCache.getOrCreate(filePath){
      val suffix = filePath.replace("/", "-")
      val tempMainContentFile = Files.createTempFile("nussknacker", suffix).toFile
      tempMainContentFile.deleteOnExit()

      val data = getClass.getClassLoader.getResourceAsStream(filePath)
      val content = Option(data).map(IOUtils.toString).getOrElse {
        logger.error(s"Failed to find $filePath - probably frontend resources are not packaged in jar. Frontend won't work properly!")
        ""
      }
      FileUtils.writeStringToFile(tempMainContentFile, content.replace("__publicPath__", publicPath))
      tempMainContentFile
    }
}
