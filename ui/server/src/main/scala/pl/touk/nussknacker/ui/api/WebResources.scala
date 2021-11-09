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
        extractRequest { matched =>
          val maybeModule = Option(getClass.getClassLoader.getResource(s"web/submodules/$moduleName"))
          val resource = Option(getClass.getClassLoader.getResource(s"web/${matched.uri.path}"))
          val maybeFile = resource.map(r => new File(r.toURI))

          (maybeModule, maybeFile) match {
            case (_, Some(file)) if file.isFile =>
              getFromFile(file)
            case (Some(_), Some(file)) if !file.isFile =>
              getFromMainFile(s"web/submodules/$moduleName/index.html")
            case (Some(_), None) =>
              logger.debug(s"Fail to load submodule file: ${matched.uri.path}")
              getFromMainFile(s"web/submodules/$moduleName/index.html")
            case (None, _) =>
              getFromMainFile(defaultMainFilePath)
          }
        }
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
