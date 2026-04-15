package pl.touk.nussknacker.http.backend

import org.polyvariant.sttp.oauth2.common
import sttp.client3.HttpError

import scala.annotation.tailrec

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

}
