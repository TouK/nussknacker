package pl.touk.nussknacker.openapi.functional

import org.apache.http.{HttpRequest, HttpResponse}
import org.apache.http.entity.{InputStreamEntity, StringEntity}
import org.apache.http.impl.bootstrap.ServerBootstrap
import org.apache.http.protocol.{HttpContext, HttpRequestHandler}
import pl.touk.nussknacker.test.AvailablePortFinder

class StubService(val swaggerDefinition: String = "/customer-swagger.json") {

  def withCustomerService[T](action: Int => T): T = {
    AvailablePortFinder.withAvailablePortsBlocked(1) { ports =>
      val port = ports.head
      val server = ServerBootstrap
        .bootstrap()
        .setListenerPort(port)
        .registerHandler(
          "/swagger",
          new HttpRequestHandler {
            override def handle(request: HttpRequest, response: HttpResponse, context: HttpContext): Unit = {
              response.setStatusCode(200)
              response.setEntity(new InputStreamEntity(getClass.getResourceAsStream(swaggerDefinition)))
            }
          }
        )
        .registerHandler(
          "/customers/*",
          new HttpRequestHandler {
            override def handle(request: HttpRequest, response: HttpResponse, context: HttpContext): Unit = {
              val id = request.getRequestLine.getUri.replaceAll(".*customers/", "").toInt
              response.setStatusCode(200)
              response.setEntity(new StringEntity(s"""{"name": "Robert Wright", "id": $id, "category": "GOLD"}"""))
            }
          }
        )
        .create()
      try {
        server.start()
        action(port)
      } finally {
        server.stop()
      }
    }
  }

}
