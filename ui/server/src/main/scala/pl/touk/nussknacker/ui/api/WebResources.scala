package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directive1, Directives, Route}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.{FileUtils, IOUtils}
import pl.touk.nussknacker.engine.util.cache.{CacheConfig, DefaultCache}
import pl.touk.nussknacker.ui.NotFoundError

import java.io.File
import java.net.URL
import java.nio.file.Files

class WebResources(publicPath: String) extends Directives with LazyLogging {

  private val mainFilesCache = new DefaultCache[String, File](CacheConfig())

  private val defaultMainFilePath = "web/static/main.html"

  val route: Route =
    pathPrefix("submodules" / Segment /) { moduleName =>
      (get & submodule(moduleName)) { _ =>
        extractRequest { matched =>
          val resource = Option(getClass.getClassLoader.getResource(s"web/${matched.uri.path}"))
          val maybeFile = resource.map(r => new File(r.toURI))

          maybeFile match {
            case Some(file) if file.isFile =>
              getFromFile(file)
            case _ =>
              logger.debug(s"Path not found: ${matched.uri.path}.")
              getFromMainFile(s"web/submodules/$moduleName/index.html")
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

    private def submodule(moduleName: String): Directive1[URL] = {
      handleExceptions(EspErrorToHttp.espErrorHandler).tflatMap { _ =>
        Option(getClass.getClassLoader.getResource(s"web/submodules/$moduleName/")) match {
          case Some(resource) => provide(resource)
          case None => failWith(SubmoduleNotFoundError(moduleName))
        }
      }
    }

    private def getFromMainFile(mainFilePath: String) = getFromFile(getFileContent(mainFilePath))

    //see config.js comment
    private def getFileContent(filePath: String): File = mainFilesCache.getOrCreate(filePath) {
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

    private case class SubmoduleNotFoundError(moduleName: String) extends RuntimeException(s"Submodule '$moduleName' not founded.") with NotFoundError
}
