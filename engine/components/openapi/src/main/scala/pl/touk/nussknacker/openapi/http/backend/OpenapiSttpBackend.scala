package pl.touk.nussknacker.openapi.http.backend

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{CollectableAction, ServiceInvocationCollector, TransmissionNames}
import pl.touk.nussknacker.openapi.http.backend.EspSttpBackend.HttpBackend
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse
import sttp.client.{NothingT, Request, Response, _}

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

object EspSttpBackend {

  type HttpBackend = SttpBackend[Future, Nothing, Nothing]

  private case class WithStringResponseBody[T](original: T, stringResponseBody: Option[String])

  private val transmissionNames = TransmissionNames("request", "response")

}

class EspSttpBackend(delegate: HttpBackend, dispatchConfig: DispatchConfig, baseLoggerName: String)
                    (implicit ec: ExecutionContext, serviceInvocationCollector: ServiceInvocationCollector) extends HttpBackend {

  import EspSttpBackend._

  private val requestLogger = Logger(LoggerFactory.getLogger(s"$baseLoggerName.request"))
  private val responseLogger = Logger(LoggerFactory.getLogger(s"$baseLoggerName.response"))

  override def send[T](request: Request[T, Nothing]): Future[Response[T]] = {
    val correlationId = UUID.randomUUID()
    lazy val requestLog = RequestResponseLog(request)
    requestLogger.debug(s"[$correlationId]: ${requestLog.pretty}")
    // Read timeout is set per request.
    val withReadTimeoutRequest = dispatchConfig.timeout.map(request.readTimeout).getOrElse(request)
    val withLoggedStringResponseBody = withReadTimeoutRequest.response(withStringResponseBody(request.response))
    val reqTime = System.currentTimeMillis()
    lazy val response = delegate.send(withLoggedStringResponseBody)
      .transform(
        response => handleResponse(correlationId, request, reqTime, response),
        error => {
          responseLogger.debug(s"[$correlationId]: request failed in ${System.currentTimeMillis() - reqTime}ms: ${requestLog.pretty}")
          error
        })
    serviceInvocationCollector.collectWithResponse(requestLog, mockValue = None)(response, transmissionNames)
  }

  private def withStringResponseBody[T](responseAs: ResponseAs[T, Nothing]): ResponseAs[WithStringResponseBody[T], Nothing] = {
    responseAs match {
      case ResponseAsByteArray =>
        ResponseAsByteArray.map(byteArray => WithStringResponseBody(byteArray, Some(new String(byteArray, StandardCharsets.UTF_8))))
      case MappedResponseAs(raw, g) =>
        val nested = withStringResponseBody(raw)
        MappedResponseAs(nested, (withSRB: WithStringResponseBody[Any], responseMetadata: ResponseMetadata) =>
          withSRB.copy(original = g(withSRB.original, responseMetadata)))
      case other =>
        other.map(WithStringResponseBody(_, None))
    }
  }

  private def handleResponse[T](correlationId: UUID,
                                request: Request[T, Nothing],
                                requestTimestamp: Long,
                                response: Response[WithStringResponseBody[T]]): CollectableAction[Response[T]] = {
    val responseLog = RequestResponseLog(request, response, resBody = response.body.stringResponseBody)
    responseLogger.debug(s"[$correlationId][took ${System.currentTimeMillis() - requestTimestamp}ms]: ${responseLog.pretty}")
    val targetResponse = response.copy(body = response.body.original)
    CollectableAction(() => responseLog, targetResponse)
  }

  override def openWebsocket[T, WS_RESULT](request: Request[T, Nothing], handler: NothingT[WS_RESULT]): Future[WebSocketResponse[WS_RESULT]] = ???

  override def close(): Future[Unit] = delegate.close()

  override def responseMonad: MonadError[Future] = delegate.responseMonad
}
