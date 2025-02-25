package pl.touk.nussknacker.ui.util

import org.apache.pekko.http.scaladsl.model.{HttpMethods, HttpResponse, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives.complete
import org.apache.pekko.http.scaladsl.server.{Directive, Directive0}

object OptionsMethodSupport {

  def apply(): Directive0 = {
    import org.apache.pekko.http.scaladsl.server.StandardRoute._
    import org.apache.pekko.http.scaladsl.server.directives.BasicDirectives._

    extractRequest.flatMap[Unit] { request =>
      request.method match {
        case HttpMethods.OPTIONS =>
          complete(HttpResponse(StatusCodes.OK))
        case _ => Directive.Empty
      }
    }
  }

}
