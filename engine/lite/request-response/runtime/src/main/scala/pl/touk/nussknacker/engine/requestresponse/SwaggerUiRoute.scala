package pl.touk.nussknacker.engine.requestresponse

import akka.http.scaladsl.model.headers.{CacheDirectives, `Cache-Control`}
import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.LazyLogging

object SwaggerUiRoute extends Directives with LazyLogging {

  val route: Route = {
    pathPrefix("swagger-ui") {
      get {
        encodeResponse {
          respondWithHeader(`Cache-Control`(List(CacheDirectives.public, CacheDirectives.`max-age`(0)))) {
            pathEndOrSingleSlash {
              getFromResource("swagger-ui/index.html")
            } ~ getFromResourceDirectory(s"swagger-ui")
          }
        }
      }
    }
  }

}
