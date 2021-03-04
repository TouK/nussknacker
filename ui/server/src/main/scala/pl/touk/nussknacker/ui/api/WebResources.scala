package pl.touk.nussknacker.ui.api

import java.nio.file.Files

import akka.http.scaladsl.server.{Directives, Route}
import org.apache.commons.io.{FileUtils, IOUtils}

class WebResources(publicPath: String) extends Directives {

  //see config.js comment
  private lazy val mainContentFile = {
    val tempMainContentFile = Files.createTempFile("nussknacker", "main.html").toFile
    tempMainContentFile.deleteOnExit()
    val data = getClass.getClassLoader.getResourceAsStream("web/static/main.html")
    val content = Option(data).map(IOUtils.toString).getOrElse("")
    FileUtils.writeStringToFile(tempMainContentFile, content.replace("__publicPath__", publicPath))
    tempMainContentFile
  }

  val route: Route =
    pathPrefix("static") {
      get {
        getFromResourceDirectory("web/static")
      }
    } ~ get {
      extractRequest { matched =>
        //main.html instead of index.html to not interfere with flink's static resources...
        getFromFile(mainContentFile) //return UI page by default to make links work
      }
      
    }

}
