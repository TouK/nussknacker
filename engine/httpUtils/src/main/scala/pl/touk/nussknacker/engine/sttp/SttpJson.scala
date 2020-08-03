package pl.touk.nussknacker.engine.sttp

import io.circe.{Decoder, Error}
import sttp.client.circe.deserializeJson
import sttp.client.{HttpError, Response, ResponseAs, ResponseError, asString}
import sttp.model.StatusCode

import scala.concurrent.Future

object SttpJson {

  def failureToFuture[T](response: Response[Either[ResponseError[Error], T]]): Future[T] = response.body match {
    case Right(qr) => Future.successful(qr)
    case Left(error) => Future.failed(error)
  }

  //we want to handle 404 as None
  def asOptionalJson[Type: Decoder]: ResponseAs[Either[ResponseError[Error], Option[Type]], Nothing] =
    asString.mapWithMetadata[Either[ResponseError[io.circe.Error], Option[Type]]] {
      case (Right(data), _) =>
        val deserialize = ResponseAs.deserializeWithError(deserializeJson[Option[Type]])
        deserialize(data)
      case (Left(_), meta) if meta.code == StatusCode.NotFound => Right(None)
      case (Left(s), meta) => Left(HttpError(s, meta.code))
    }

}
