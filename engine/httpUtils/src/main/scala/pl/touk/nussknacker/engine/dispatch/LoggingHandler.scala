package pl.touk.nussknacker.engine.dispatch

import java.nio.charset.StandardCharsets

import org.asynchttpclient._
import com.typesafe.scalalogging.Logger
import io.netty.handler.codec.http.HttpHeaders

private class LoggingHandler[T] (handler: AsyncHandler[T], logger: Logger, id: String)
  extends AsyncHandler[T] {
  import LoggingHandler._
  override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): AsyncHandler.State = {
    logger.debug(s"[$id] body: ${new String(bodyPart.getBodyPartBytes, charset)}")
    handler.onBodyPartReceived(bodyPart)
  }

  override def onHeadersReceived(headers: HttpHeaders): AsyncHandler.State = {
    logger.debug(s"[$id] headers: ${mapHeaders(headers)}")
    handler.onHeadersReceived(headers)
  }

  override def onStatusReceived(responseStatus: HttpResponseStatus): AsyncHandler.State = {
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

  def mapHeaders(headers: HttpHeaders): Map[String, List[String]] = {
    headers.entries()
      .asScala
      .groupBy(_.getKey)
      .map { e =>
        (e._1, e._2.map(_.getValue).toList)
      }
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