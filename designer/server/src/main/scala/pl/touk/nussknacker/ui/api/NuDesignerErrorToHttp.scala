package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.ExceptionHandler
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Encoder
import pl.touk.nussknacker.ui._

import scala.language.implicitConversions
import scala.util.control.NonFatal

object NuDesignerErrorToHttp extends LazyLogging with FailFastCirceSupport {
  private type ErrorMessage = String

  def nuDesignerErrorHandler: ExceptionHandler = {
    import akka.http.scaladsl.server.Directives._
    ExceptionHandler { case NonFatal(e) =>
      complete(toHttpResponse(errorToStatusAndMessage(e)))
    }
  }

  def errorToStatusAndMessage: Throwable => (StatusCode, ErrorMessage) = {
    case error: NuDesignerError =>
      logError(error)
      toStatusAndMessage(error)
    case ex: IllegalArgumentException =>
      logger.debug(s"Illegal argument: ${ex.getMessage}", ex)
      (StatusCodes.BadRequest, ex.getMessage)
    case ex =>
      logger.error(s"Unknown error: ${ex.getMessage}", ex)
      (StatusCodes.InternalServerError, ex.getMessage)
  }

  def toResponseEither[T: Encoder](either: Either[NuDesignerError, T]): ToResponseMarshallable = either match {
    case Right(t)  => t
    case Left(err) => httpResponseFrom(err)
  }

  def httpResponseFrom(error: NuDesignerError): HttpResponse = {
    toHttpResponse(toStatusAndMessage(error))
  }

  private def toStatusAndMessage(error: NuDesignerError): (StatusCode, ErrorMessage) =
    (
      httpStatusCodeFrom(error),
      error.getMessage
    )

  private def toHttpResponse(statusAndMessage: (StatusCode, ErrorMessage)): HttpResponse = {
    HttpResponse(
      status = statusAndMessage._1,
      entity = statusAndMessage._2
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
