package cors

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object CorsSupport {

  val optionsSupport = {
    options {complete("")}
  }

  val corsHeaders = List(RawHeader("Access-Control-Allow-Origin", "*"),
    RawHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS, DELETE"),
    RawHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization") )

  def cors(r: Route) = {
    respondWithHeaders(corsHeaders) { r ~ optionsSupport}
  }

}