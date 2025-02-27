package pl.touk.nussknacker.http.backend

import io.circe.{Decoder, Error}
import sttp.client3.{asString, HttpError, Response, ResponseAs, ResponseException}
import sttp.client3.circe.deserializeJson
import sttp.model.StatusCode
import sttp.monad.MonadError

import scala.concurrent.Future
import scala.language.higherKinds

object SttpJson {

  def failureToError[F[_], T](
      response: Response[Either[ResponseException[String, Error], T]]
  )(implicit monadError: MonadError[F]): F[T] = response.body match {
    case Right(qr)   => monadError.unit(qr)
    case Left(error) => monadError.error(error)
  }

  def failureToFuture[T](response: Response[Either[ResponseException[String, Error], T]]): Future[T] =
    response.body match {
      case Right(qr)   => Future.successful(qr)
      case Left(error) => Future.failed(error)
    }

  // we want to handle 404 as None
  def asOptionalJson[Type: Decoder]: ResponseAs[Either[ResponseException[String, Error], Option[Type]], Any] =
    asString.mapWithMetadata[Either[ResponseException[String, Error], Option[Type]]] {
      case (Right(data), _) =>
        val deserialize = ResponseAs.deserializeWithError(deserializeJson[Option[Type]])
        deserialize(data)
      case (Left(_), meta) if meta.code == StatusCode.NotFound => Right(None)
      case (Left(s), meta)                                     => Left(HttpError(s, meta.code))
    }

}
