package pl.touk.nussknacker.ui.util

import org.typelevel.ci._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives.respondWithHeaders

object SecurityHeadersSupport {

  val headers = List(
    (ci"X-Content-Type-Options", "nosniff"),
    (ci"Referrer-Policy", "no-referrer")
  )

  private val rawHeaders = headers.map { case (name, value) => RawHeader(name.toString, value) }

  def apply(): Directive0 = respondWithHeaders(rawHeaders)
}
