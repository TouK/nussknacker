package cors

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.MethodRejection
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.model.headers.`Access-Control-Allow-Headers`
import akka.http.scaladsl.model.headers.`Access-Control-Allow-Methods`
import akka.http.scaladsl.model.headers.`Access-Control-Allow-Origin`
import akka.http.scaladsl.model.headers.`Access-Control-Allow-Credentials`
import akka.http.scaladsl.model.headers.`Access-Control-Max-Age`

object CorsSupport {

  def cors[T](corsOptions: CorsOptions = defaultCorsOptions): Directive0 = {
    def corsRejectionHandler(allowOrigin: `Access-Control-Allow-Origin`) = RejectionHandler
      .newBuilder().handle {
      case MethodRejection(supported) =>
        complete(HttpResponse().withHeaders(
          `Access-Control-Allow-Methods`(OPTIONS, supported) :: allowOrigin :: corsOptions.optionsCorsHeaders
        ))
    }
      .result()

    def originToAllowOrigin(origin: Origin): Option[`Access-Control-Allow-Origin`] = {
      if (corsOptions.corsAllowOrigins.contains("*") || corsOptions.corsAllowOrigins.contains(origin.value))
        origin.origins.headOption.map(`Access-Control-Allow-Origin`.apply)
      else
        None
    }

    mapInnerRoute { route => context =>
      ((context.request.method, context.request.header[Origin].flatMap(originToAllowOrigin)) match {
        case (OPTIONS, Some(allowOrigin)) =>
          handleRejections(corsRejectionHandler(allowOrigin)) {
            respondWithHeaders(allowOrigin, `Access-Control-Allow-Credentials`(corsOptions.corsAllowCredentials)) {
              route
            }
          }
        case (_, Some(allowOrigin)) =>
          respondWithHeaders(allowOrigin, `Access-Control-Allow-Credentials`(corsOptions.corsAllowCredentials)) {
            route
          }
        case (_, _) =>
          route
      })(context)
    }
  }

  case class CorsOptions(corsAllowOrigins: List[String],
                         corsAllowedHeaders: List[String],
                         corsAllowCredentials: Boolean,
                         optionsCorsHeaders: List[HttpHeader]
                        )

  val defaultCorsOptions = {
    val corsAllowedHeaders = List("Origin", "X-Requested-With", "Content-Type", "Accept", "Accept-Encoding", "Accept-Language", "Host", "Referer", "User-Agent")
    val corsAllowCredentials = true
    CorsOptions(
      corsAllowOrigins = List("*"),
      corsAllowedHeaders = corsAllowedHeaders,
      corsAllowCredentials = corsAllowCredentials,
      optionsCorsHeaders = List[HttpHeader](
        `Access-Control-Allow-Headers`(corsAllowedHeaders.mkString(", ")),
        `Access-Control-Max-Age`(3600),
        `Access-Control-Allow-Credentials`(corsAllowCredentials)
      )
    )
  }
}