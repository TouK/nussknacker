package pl.touk.esp.ui.api

import akka.http.scaladsl.server.Directives

object WebResources extends Directives {

  val route =
    pathPrefix("static") {
      get {
        getFromResourceDirectory("web/static")
      }
    } ~ get {
      //main.html instead of index.html to not interfere with flink's static resources...
      getFromResource("web/main.html") //return UI page by default to make links work
    }

}
