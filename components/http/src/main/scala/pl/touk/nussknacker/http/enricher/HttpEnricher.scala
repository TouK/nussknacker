package pl.touk.nussknacker.http.enricher

import io.circe
import io.circe.Json
import org.apache.commons.lang3.NotImplementedException
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.{Context, ServiceInvoker}
import pl.touk.nussknacker.engine.util.json.JsonSchemaUtils._
import pl.touk.nussknacker.http.backend.HttpBackendProvider
import sttp.client3.{Identity, RequestT, Response, ResponseException, basicRequest}
import sttp.client3.circe.asJson
import sttp.model.{Header, Method, Uri}
import pl.touk.nussknacker.engine.util.json.JsonUtils

import java.util
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

case class RequestData(uri: Uri, method: Method, headers: List[Header])

class HttpEnricher(clientProvider: HttpBackendProvider, requestData: RequestData) extends ServiceInvoker {

  override def invoke(context: Context)(
      implicit ec: ExecutionContext,
      collector: InvocationCollectors.ServiceInvocationCollector,
      componentUseCase: ComponentUseCase
  ): Future[Any] = {
    val httpClient = clientProvider.httpBackendForEc
    val response   = httpClient.send(buildRequest(requestData))

//    TODO: how much info to return about the request?
    response.map { res =>
      new java.util.HashMap[String, Any](
        Map(
          "statusCode" -> res.code.code,
          "statusText" -> res.statusText,
          "headers" ->
            new java.util.HashMap[String, Any](
              res.headers
                .map { h =>
                  h.name -> h.value
                }
                .toMap
                .asJava
            ),
          "body" -> {
            res.body match {
              case Left(err) => null
              case Right(bodyStr) =>
                io.circe.parser.parse(bodyStr) match {
                  // TODO: do we want to pass non-jsons as strings? getting a big html could blow up memory usage, but it
                  //  would be soo cool to do web-scraping with some utils :D
                  case Left(str)   => bodyStr
                  case Right(json) => JsonUtils.jsonToAny(json)
                }
            }
          }
        ).asJava
      )
    }
  }

  private def buildRequest(spec: RequestData): RequestT[Identity, Either[String, String], Any] = {
    basicRequest.method(spec.method, spec.uri).headers(spec.headers: _*)
  }

}
