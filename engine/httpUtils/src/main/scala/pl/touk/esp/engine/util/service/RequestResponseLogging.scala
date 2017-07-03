package pl.touk.esp.engine.util.service

import com.ning.http.client.Response
import com.typesafe.scalalogging.LazyLogging
import dispatch.Req

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait RequestResponseLogging extends LazyLogging {

  def logRequestResponse(req: Req)
                        (id: String)
                        (sendRequestAction: => Future[Response])
                        (implicit executionContext: ExecutionContext): Future[Response] = {
    import collection.convert.wrapAsScala._
    val request = req.toRequest
    debug(
      s"""${request.getMethod} ${req.url} $id request:
         | HEADERS:
         |${request.getHeaders.entrySet().map(e => "  " + e.getKey + ": " + e.getValue.mkString(" ")).mkString("\n")}
         | CONTENT:
         |  ${Option(request.getStringData).getOrElse("")}""".stripMargin)
    val result = sendRequestAction
    result.onComplete {
      case Success(response) =>
        debug(
          s"""${request.getMethod} ${req.url} $id response:
             | STATUS: ${response.getStatusCode} ${response.getStatusText}
             | HEADERS:
             |${response.getHeaders.entrySet().map(e => "  " + e.getKey + ": " + e.getValue.mkString(" ")).mkString("\n")}
             | CONTENT:
             |  ${response.getResponseBody}""".stripMargin)
      case Failure(ex) =>
        logFailure(req, id, ex)
    }
    result
  }

  def logFailure(req: Req, id: String, ex: Throwable): Unit = {
    logger.error(s"${req.url} $id error", ex)
  }

  def debug(obj: =>String) = logger.debug(obj)
}