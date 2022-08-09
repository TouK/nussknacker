package pl.touk.nussknacker.ui.util

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives.respondWithHeaders

object ContentTypeOptionsSecureSupport {
  val header = RawHeader("X-Content-Type-Options", "nosniff")

  def apply(): Directive0 = respondWithHeaders(header)
}
