package pl.touk.nussknacker.ui.api

import org.apache.pekko.http.scaladsl.marshalling.ToResponseMarshallable
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.ExceptionHandler
import com.typesafe.scalalogging.LazyLogging
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import io.circe.Encoder
import pl.touk.nussknacker.ui._

import java.util.concurrent.ExecutionException
import scala.language.implicitConversions
import scala.util.control.NonFatal

object NuDesignerErrorToHttp extends LazyLogging with FailFastCirceSupport {

  def nuDesignerErrorHandler: ExceptionHandler = {
    import org.apache.pekko.http.scaladsl.server.Directives._
    ExceptionHandler { case NonFatal(e) =>
      complete(errorToHttpResponse(unwrapException(e)))
    }
  }

  def unwrapException(e: Throwable): Throwable = e match {
    case ex: ExecutionException => ex.getCause
    case other                  => other
  }

  private def errorToHttpResponse(e: Throwable): HttpResponse = e match {
    case error: NuDesignerError =>
      logError(error)
      httpResponseFrom(error)
    case ex: IllegalArgumentException =>
      logger.debug(s"Illegal argument: ${ex.getMessage}", ex)
      HttpResponse(status = StatusCodes.BadRequest, entity = ex.getMessage)
    case ex: NotImplementedError =>
      logger.error(s"Not implemented: ${ex.getMessage}", ex)
      HttpResponse(status = StatusCodes.NotImplemented, entity = ex.getMessage)
    case ex =>
      logger.error(s"Unknown error: ${ex.getMessage}", ex)
      HttpResponse(status = StatusCodes.InternalServerError, entity = ex.getMessage)
  }

  def toResponseEither[T: Encoder](either: Either[NuDesignerError, T]): ToResponseMarshallable = either match {
    case Right(t)  => t
    case Left(err) => httpResponseFrom(err)
  }

  def httpResponseFrom(error: NuDesignerError): HttpResponse = {
    HttpResponse(
      status = httpStatusCodeFrom(error),
      entity = error.getMessage
    )
  }

  private def logError(error: NuDesignerError): Unit = error match {
    case error: NotFoundError =>
      logger.debug(s"Not found error: ${error.getMessage}. ${returnedHttpStatusInfo(error)}", error)
    case error: BadRequestError =>
      logger.debug(s"Bad request error: ${error.getMessage}. ${returnedHttpStatusInfo(error)}", error)
    case error: UnauthorizedError =>
      logger.debug(s"Unauthorized error: ${error.getMessage}. ${returnedHttpStatusInfo(error)}", error)
    case error: IllegalOperationError =>
      // we decided to use WARN level here because we are not sure if the Illegal Operation Error is caused by client
      // mistake or Nu malfunction (or Nu's dependency inconsistency - eg. external modification of Schema Registry)
      logger.warn(
        s"Illegal operation error: ${error.getMessage}. ${returnedHttpStatusInfo(error)} Error details: [${error.details}].",
        error
      )
    case error: OtherError =>
      logger.debug(s"Other error: ${error.getMessage}. ${returnedHttpStatusInfo(error)}", error)
    case error: FatalError =>
      logger.error(s"Fatal error: ${error.getMessage}. ${returnedHttpStatusInfo(error)}", error)
  }

  private def returnedHttpStatusInfo(error: NuDesignerError) = {
    s"Http status ${httpStatusCodeFrom(error).intValue()} will be returned."
  }

  private def httpStatusCodeFrom(error: NuDesignerError): StatusCode = error match {
    case _: NotFoundError         => StatusCodes.NotFound
    case _: FatalError            => StatusCodes.InternalServerError
    case _: BadRequestError       => StatusCodes.BadRequest
    case _: UnauthorizedError     => StatusCodes.Unauthorized
    case _: IllegalOperationError => StatusCodes.Conflict
    case _: OtherError            => StatusCodes.InternalServerError
  }

}
