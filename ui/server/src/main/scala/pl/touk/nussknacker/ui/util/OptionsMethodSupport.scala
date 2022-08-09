package pl.touk.nussknacker.ui.util

import akka.http.scaladsl.model.{HttpMethods, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.{Directive, Directive0}

object OptionsMethodSupport {

  def apply(): Directive0 = {
    import akka.http.scaladsl.server.StandardRoute._
    import akka.http.scaladsl.server.directives.BasicDirectives._

    extractRequest.flatMap[Unit] { request =>
      request.method match {
        case HttpMethods.OPTIONS =>
          complete(HttpResponse(StatusCodes.OK))
        case _ => Directive.Empty
      }
    }
  }
}
