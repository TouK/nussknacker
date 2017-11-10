package pl.touk.nussknacker.engine.dispatch

import java.nio.charset.StandardCharsets

import com.ning.http.client._
import com.typesafe.scalalogging.Logger

private class LoggingHandler[T] (handler: AsyncHandler[T], logger: Logger, id: String)
  extends AsyncHandler[T] {
  import LoggingHandler._
  override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): AsyncHandler.STATE = {
    logger.debug(s"[$id] body: ${new String(bodyPart.getBodyPartBytes, charset)}")
    handler.onBodyPartReceived(bodyPart)
  }

  override def onHeadersReceived(headers: HttpResponseHeaders): AsyncHandler.STATE = {
    logger.debug(s"[$id] headers: ${mapHeaders(headers.getHeaders)}")
    handler.onHeadersReceived(headers)
  }

  override def onStatusReceived(responseStatus: HttpResponseStatus): AsyncHandler.STATE = {
    val message = s"[$id]" +
      s" status code: ${responseStatus.getStatusCode}" +
      s" status rext: ${responseStatus.getStatusText}"
    logger.debug(message)
    handler.onStatusReceived(responseStatus)
  }

  override def onCompleted(): T = {
    val result = handler.onCompleted()
    logger.debug(s"[$id] recived object: $result")
    result
  }

  override def onThrowable(t: Throwable): Unit = {
    logger.error(s"[$id] request casued error", t)
    handler.onThrowable(t)
  }
}

object LoggingHandler {
  def apply[T](handler: AsyncHandler[T], logger: Logger, id:String): AsyncHandler[T] =
    new LoggingHandler(handler, logger, id)

  val charset: String = StandardCharsets.UTF_8.name()
  import scala.collection.JavaConverters._

  def mapHeaders(headers: FluentCaseInsensitiveStringsMap): Map[String, List[String]] = {
    headers.entrySet()
      .asScala
      .map { e =>
        (e.getKey, e.getValue.asScala.toList)
      } toMap
  }

  case class LogRequest(
                         url: String,
                         method: String,
                         headers: Map[String, List[String]],
                         stringData: Option[String]
                       )

  object LogRequest {
    def apply(req: Request): LogRequest =
      new LogRequest(
        req.getUrl,
        req.getMethod,
        mapHeaders(req.getHeaders),
        Option(req.getStringData)
      )
  }
}