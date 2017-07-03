package pl.touk.esp.ui.api

import akka.http.scaladsl.server.Directives

object WebResources extends Directives {

  val route =
    pathPrefix("static") {
      get {
        getFromResourceDirectory("web/static")
      }
    } ~ get {
      //to jest main a nie index.html zeby nie gryzlo sie z niektorymi rzeczami z flinka...
      getFromResource("web/main.html") //defaultowo zwracamy strone aplikacji, bez tego nie dzialaja linki we frontendzie
    }

}
