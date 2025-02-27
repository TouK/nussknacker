package pl.touk.nussknacker.http.backend

import com.typesafe.scalalogging.LazyLogging
import sttp.client3.{HttpError, Response}
import sttp.model.StatusCode

import scala.concurrent.Future

object HttpClientErrorHandler extends LazyLogging {

  def handleUnitResponse(action: String, message: Option[String] = None)(
      response: Response[Either[String, String]]
  ): Future[Unit] = (response.code, response.body) match {
    case (code, Right(_)) if code.isSuccess => Future.successful(())
    case (code, Right(body))                => handleClientError(body, code, action, message)
    case (code, Left(error))                => handleClientError(error, code, action, message)
  }

  def recoverWithMessage[T](action: String, message: Option[String] = None): PartialFunction[Throwable, Future[T]] = {
    // extract nested HttpError: it's most likely wrapped in SttpClientException
    case HttpErrorExtractor(HttpError(body, status)) => handleClientError(s"$body", status, action, message)
  }

  // We don't want to pass error directly to user, as it usually contains stacktrace etc.
  private def handleClientError(
      body: String,
      status: StatusCode,
      action: String,
      message: Option[String]
  ): Future[Nothing] = {
    logger.error(s"Failed to $action, status code: $status, errors: $body.")
    val errorMessage = message.getOrElse(s"Failed to $action. Detailed error information in logs.")
    Future.failed(HttpClientError(errorMessage))
  }

  private object HttpErrorExtractor {
    def unapply(t: Throwable): Option[HttpError[_]] = HttpError.find(t)
  }

}

case class HttpClientError(message: String) extends Exception(message)
