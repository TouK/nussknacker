package pl.touk.nussknacker.ui.util

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directive
import akka.http.scaladsl.server.Directives.respondWithHeaders

object CorsSupport {

  val headers = List(RawHeader("Access-Control-Allow-Origin", "http://localhost:3000"),
    RawHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS, PATCH, DELETE"),
    RawHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization"),
    RawHeader("Access-Control-Allow-Credentials", "true")
  )

  def cors(enabled: Boolean) = {
    if (enabled) {
      respondWithHeaders(headers)
    } else {
      Directive.Empty
    }
  }
}