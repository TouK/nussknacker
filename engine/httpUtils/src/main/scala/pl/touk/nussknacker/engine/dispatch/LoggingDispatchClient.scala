package pl.touk.nussknacker.engine.dispatch

import java.util.concurrent.atomic.AtomicLong

import com.ning.http.client._
import com.typesafe.scalalogging.Logger
import dispatch.{Future, Http}
import org.slf4j.LoggerFactory
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.dispatch.LoggingHandler.LogRequest

import scala.concurrent.ExecutionContext

private class LoggingDispatchClient (serviceName: String, http: Http, id: => String, collector: Option[ServiceInvocationCollector])
  extends Http(client = http.client) {
  private val logger = Logger(LoggerFactory.getLogger(serviceName))
  override def apply[T](request: Request, handler: AsyncHandler[T])
                       (implicit executor: ExecutionContext): Future[T] = {
    val uniqueId:String = id
    logger.debug(s"[$uniqueId]request: ${LogRequest(request)}")
    val loggerHandler =
      LoggingHandler[T](handler, logger, uniqueId)
    http.apply(request, loggerHandler)
  }

}

object LoggingDispatchClient {
  private val uniqueId = new AtomicLong()

  def apply(serviceName: String, http: Http, id: => String = correlationId, collector: Option[ServiceInvocationCollector] = None): Http =
    new LoggingDispatchClient(serviceName: String, http, id, collector)

  def correlationId: String = uniqueId.incrementAndGet().toString
}
