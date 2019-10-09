package pl.touk.nussknacker.engine.sttp

import com.softwaremill.sttp.{DeserializationError, Response}

import scala.concurrent.Future

object SttpJson {

  def failureToFuture[T](response: Response[Either[DeserializationError[io.circe.Error], T]]): Future[T] = response.body match {
    case Right(Right(qr)) => Future.successful(qr)
    case Right(Left(error)) => Future.failed(error.error)
    case Left(error) => Future.failed(new RuntimeException(s"Request failed: $error, code: ${response.code}"))
  }

}
