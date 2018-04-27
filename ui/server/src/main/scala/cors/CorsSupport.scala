package cors

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpMethods, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directive
import akka.http.scaladsl.server.Directives._

object CorsSupport {

  private val corsHeaders = List(RawHeader("Access-Control-Allow-Origin", "http://localhost:3000"),
    RawHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS, PATCH, DELETE"),
    RawHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization"),
    RawHeader("Access-Control-Allow-Credentials", "true")
  )

  def cors(enabled: Boolean) = {
    import akka.http.scaladsl.server.StandardRoute._
    import akka.http.scaladsl.server.directives.BasicDirectives._

    if (enabled) {
      extractRequest.flatMap[Unit] { request =>
        request.method match {
          case HttpMethods.OPTIONS =>
            complete(HttpResponse(StatusCodes.OK, corsHeaders))
          case _ =>
            respondWithHeaders(corsHeaders)
        }
      }
    } else {
      Directive.Empty
    }
  }

}