package pl.touk.nussknacker.http.backend

import org.polyvariant.sttp.oauth2.common
import pl.touk.nussknacker.engine.util.service.ReturnErrors
import sttp.client3.HttpError

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

object HttpStatusCodeExtractor {

  def extract(error: Throwable): Option[java.lang.Integer] = extract(error, PartialFunction.empty)

  def extract(
      error: Throwable,
      additionalExtractors: PartialFunction[Throwable, java.lang.Integer]
  ): Option[java.lang.Integer] = {
    @tailrec
    def loop(throwable: Throwable): Option[java.lang.Integer] = throwable match {
      case null                                             => None
      case httpError: HttpError[_]                          => Some(Int.box(httpError.statusCode.code))
      case httpClientError: common.Error.HttpClientError    => Some(Int.box(httpClientError.statusCode.code))
      case other if additionalExtractors.isDefinedAt(other) => Some(additionalExtractors(other))
      case other                                            => loop(other.getCause)
    }

    loop(error)
  }

  def handleHttpResult[T](
      returnErrors: ReturnErrors[T],
      invokeResult: Future[(T, Option[java.lang.Integer])],
      additionalExtractors: PartialFunction[Throwable, java.lang.Integer] = PartialFunction.empty
  )(implicit ec: ExecutionContext): Future[AnyRef] =
    ReturnErrors
      .handleWithStatus(
        returnErrors,
        invokeResult,
        errorStatusCode = extract(_, additionalExtractors)
      )
      .map(_.asInstanceOf[AnyRef])

}
