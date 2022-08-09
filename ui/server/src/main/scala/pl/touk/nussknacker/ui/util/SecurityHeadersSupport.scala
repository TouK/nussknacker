package pl.touk.nussknacker.ui.util

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives.respondWithHeaders

object SecurityHeadersSupport {
  val headers = List(RawHeader("X-Content-Type-Options", "nosniff"), RawHeader("Referrer-Policy", "no-referrer"))

  def apply(): Directive0 = respondWithHeaders(headers)
}
