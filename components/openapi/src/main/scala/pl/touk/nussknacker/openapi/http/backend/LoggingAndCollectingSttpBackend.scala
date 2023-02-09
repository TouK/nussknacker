package pl.touk.nussknacker.openapi.http.backend

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{CollectableAction, ServiceInvocationCollector, TransmissionNames}
import sttp.capabilities.Effect
import sttp.client3._
import sttp.model.ResponseMetadata

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

object LoggingAndCollectingSttpBackend {

  private case class WithStringResponseBody[T](original: T, stringResponseBody: () => Option[String])

  private val transmissionNames = TransmissionNames("request", "response")

}

class LoggingAndCollectingSttpBackend[+P](delegate: SttpBackend[Future, P], baseLoggerName: String)
                                         (implicit ec: ExecutionContext, serviceInvocationCollector: ServiceInvocationCollector) extends DelegateSttpBackend[Future, P](delegate) {

  import LoggingAndCollectingSttpBackend._

  private val requestLogger = Logger(LoggerFactory.getLogger(s"$baseLoggerName.request"))
  private val responseLogger = Logger(LoggerFactory.getLogger(s"$baseLoggerName.response"))

  override def send[T, R >: P with Effect[Future]](request: Request[T, R]): Future[Response[T]] = {
    val correlationId = UUID.randomUUID()
    lazy val requestLog = RequestResponseLog(request)
    requestLogger.debug(s"[$correlationId]: ${requestLog.pretty}")
    val withLoggedStringResponseBody = request.response(withStringResponseBody(request.response))
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

  private def withStringResponseBody[T, R >: P with Effect[Future]](responseAs: ResponseAs[T, R]): ResponseAs[WithStringResponseBody[T], R] = {
    responseAs match {
      case ResponseAsByteArray =>
        ResponseAsByteArray.map(byteArray => WithStringResponseBody(byteArray, () => Some(new String(byteArray, StandardCharsets.UTF_8))))
      case MappedResponseAs(raw, g, showAs) =>
        val nested = withStringResponseBody(raw)
        MappedResponseAs(
          nested,
          (withSRB: WithStringResponseBody[Any], responseMetadata: ResponseMetadata) =>
            withSRB.copy(original = g(withSRB.original, responseMetadata)),
          showAs
        )
      case other =>
        other.map(WithStringResponseBody(_, () => None))
    }
  }

  private def handleResponse[T, R >: P with Effect[Future]](correlationId: UUID,
                                request: Request[T, R],
                                requestTimestamp: Long,
                                response: Response[WithStringResponseBody[T]]): CollectableAction[Response[T]] = {
    def responseLog = RequestResponseLog(request, response, resBody = response.body.stringResponseBody())
    responseLogger.debug(s"[$correlationId][took ${System.currentTimeMillis() - requestTimestamp}ms]: ${responseLog.pretty}")
    val targetResponse = response.copy(body = response.body.original)
    CollectableAction(() => responseLog, targetResponse)
  }
}
