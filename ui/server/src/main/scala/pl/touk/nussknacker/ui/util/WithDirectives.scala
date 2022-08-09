package pl.touk.nussknacker.ui.util

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpMethods, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives.{complete, respondWithHeaders}

object WithDirectives {
  def apply(directives: Directive0*): Directive0 = directives.reduce((d1, d2) => d1.and(d2))
}

object WithHeaders {

  def apply(header: RawHeader): Directive0 = apply(List(header))

  def apply(headers: List[RawHeader]): Directive0 = {
    import akka.http.scaladsl.server.StandardRoute._
    import akka.http.scaladsl.server.directives.BasicDirectives._

    extractRequest.flatMap[Unit] { request =>
      request.method match {
        case HttpMethods.OPTIONS =>
          complete(HttpResponse(StatusCodes.OK, headers))
        case _ =>
          respondWithHeaders(headers)
      }
    }
  }
}