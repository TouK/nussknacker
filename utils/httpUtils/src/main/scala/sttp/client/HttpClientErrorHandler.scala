package sttp.client

import com.typesafe.scalalogging.LazyLogging
import sttp.model.StatusCode

import scala.concurrent.Future

object HttpClientErrorHandler extends LazyLogging {

  def handleUnitResponse(action: String, message: Option[String] = None)(response: Response[Either[String, String]]): Future[Unit] = (response.code, response.body) match {
    case (code, Right(_)) if code.isSuccess => Future.successful(())
    case (code, Left(error)) => handleClientError(error, code, action, message)
  }

  def recoverWithMessage[T](action: String, message: Option[String] = None): PartialFunction[Throwable, Future[T]] = {
    case HttpError(body, status) => handleClientError(body, status, action, message)
  }

  //We don't want to pass error directly to user, as it usually contains stacktrace etc.
  private def handleClientError(body: String, status: StatusCode, action: String, message: Option[String]): Future[Nothing] = {
    logger.error(s"Failed to $action, status code: $status, errors: $body.")
    val errorMessage = message.getOrElse(s"Failed to $action. Detailed error information in logs.")
    Future.failed(HttpClientError(errorMessage))
  }

}

case class HttpClientError(message: String) extends Exception(message)
