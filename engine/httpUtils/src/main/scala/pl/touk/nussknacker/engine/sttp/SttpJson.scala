package pl.touk.nussknacker.engine.sttp

import io.circe
import io.circe.Decoder
import sttp.client.circe.deserializeJson
import sttp.client.{HttpError, Response, ResponseAs, ResponseError, asString}
import sttp.model.StatusCode

import scala.concurrent.Future

object SttpJson {

  def failureToFuture[T](response: Response[Either[ResponseError[io.circe.Error], T]]): Future[T] = response.body match {
    case Right(qr) => Future.successful(qr)
    case Left(error) => Future.failed(error)
  }

  //we want to handle 404 as None
  def asOptionalJson[Type: Decoder]: ResponseAs[Either[ResponseError[circe.Error], Option[Type]], Nothing] =
    asString.mapWithMetadata[Either[ResponseError[io.circe.Error], Option[Type]]] {
      case (Right(data), _) => ResponseAs.deserializeWithError(deserializeJson[Option[Type]])(data)
      case (Left(_), meta) if meta.code == StatusCode.NotFound => Right(None)
      case (Left(s), _) => Left(HttpError(s))
    }

}
