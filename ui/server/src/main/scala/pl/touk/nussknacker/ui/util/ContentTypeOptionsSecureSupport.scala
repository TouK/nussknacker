package pl.touk.nussknacker.ui.util

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.{Directive0, Route}

object ContentTypeOptionsSecureSupport extends Directive0 {
  val header = RawHeader("X-Content-Type-Options", "nosniff")

  override def tapply(f: Unit => Route): Route = WithHeaders(header).tapply(f)
}
