package pl.touk.nussknacker.ui.util

import org.apache.pekko.http.scaladsl.model.headers._
import org.apache.pekko.http.scaladsl.server.Directives.respondWithHeaders
import org.apache.pekko.http.scaladsl.server.{Directive, Directive0}
import org.typelevel.ci._

object CorsSupport {

  val headers = List(
    (ci"Access-Control-Allow-Origin", "http://localhost:3000"),
    (ci"Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS, PATCH, DELETE"),
    (ci"Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization"),
    (ci"Access-Control-Allow-Credentials", "true")
  )

  private val rawHeaders = headers.map { case (name, value) => RawHeader(name.toString, value) }

  def cors(enabled: Boolean): Directive0 = {
    if (enabled) {
      respondWithHeaders(rawHeaders)
    } else {
      Directive.Empty
    }
  }

}
