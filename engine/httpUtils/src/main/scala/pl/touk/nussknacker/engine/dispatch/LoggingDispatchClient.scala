package pl.touk.nussknacker.engine.dispatch

import java.util.concurrent.atomic.AtomicLong

import com.ning.http.client._
import com.typesafe.scalalogging.Logger
import dispatch.{Future, Http}
import org.slf4j.LoggerFactory
import pl.touk.nussknacker.engine.dispatch.LoggingHandler.LogRequest

import scala.concurrent.ExecutionContext

private class LoggingDispatchClient (classForLogging: Class[_], http: Http, id: => String)
  extends Http {
  private val logger = Logger(LoggerFactory.getLogger(classForLogging.getName))

  override def apply[T](request: Request, handler: AsyncHandler[T])
                       (implicit executor: ExecutionContext): Future[T] = {
    val uniueId:String = id
    logger.debug(s"[$uniueId]request: ${LogRequest(request)}")
    val loggerHandler =
      LoggingHandler[T](handler, logger, uniueId)
    http.apply(request, loggerHandler)
  }
}

object LoggingDispatchClient {
  private val uniqueId = new AtomicLong()

  def apply(classForLogging: Class[_], http: Http = Http(), id: => String = correlationId): Http =
    new LoggingDispatchClient(classForLogging, http, id)

  def correlationId: String = uniqueId.incrementAndGet().toString
}
