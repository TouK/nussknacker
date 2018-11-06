package pl.touk.nussknacker.engine.dispatch

import java.util.concurrent.atomic.AtomicLong

import com.typesafe.scalalogging.Logger
import dispatch.{Future, Http, HttpExecutor}
import org.asynchttpclient.{AsyncHandler, AsyncHttpClient, Request}
import org.slf4j.LoggerFactory
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.dispatch.LoggingHandler.LogRequest

import scala.concurrent.ExecutionContext

private class LoggingDispatchClient (serviceName: String, val client: AsyncHttpClient, id: => String, collector: Option[ServiceInvocationCollector])
//TODO: is passing builders ok??
  extends HttpExecutor {
  private val logger = Logger(LoggerFactory.getLogger(serviceName))

  override def apply[T](request: Request, handler: AsyncHandler[T])
                       (implicit executor: ExecutionContext): Future[T] = {
    val uniqueId:String = id
    logger.debug(s"[$uniqueId]request: ${LogRequest(request)}")
    val loggerHandler =
      LoggingHandler[T](handler, logger, uniqueId)
    super.apply(request, loggerHandler)
  }

}

object LoggingDispatchClient {
  private val uniqueId = new AtomicLong()

  def apply(serviceName: String, client: AsyncHttpClient, id: => String, collector: Option[ServiceInvocationCollector]): HttpExecutor =
    new LoggingDispatchClient(serviceName: String, client, id, collector)

  //TODO: pass AsyncHttpClient?
  def apply(serviceName: String, http: Http, id: => String = correlationId, collector: Option[ServiceInvocationCollector] = None): HttpExecutor =
    apply(serviceName: String, http.client, id, collector)

  def correlationId: String = uniqueId.incrementAndGet().toString
}
