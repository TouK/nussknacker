package pl.touk.esp.ui.api

import akka.http.scaladsl.server.Directives

object WebResources extends Directives {

  val route = //fixme tutaj powinno byc cos w stylu pathPrefix not 'api'
    pathPrefix("static") {
      get {
        getFromResourceDirectory("web/static")
      }
    } ~ get {
      getFromResource("web/index.html") //defaultowo zwracamy strone aplikacji, bez tego nie dzialaja linki we frontendzie
    }

}
