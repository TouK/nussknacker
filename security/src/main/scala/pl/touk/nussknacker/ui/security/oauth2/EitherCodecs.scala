package pl.touk.nussknacker.ui.security.oauth2

import io.circe._

import scala.concurrent.duration.{Deadline, FiniteDuration, SECONDS}

protected[oauth2] trait EitherCodecs {
  implicit def eitherDecoder[A, B](implicit a: Decoder[A], b: Decoder[B]): Decoder[Either[A, B]] = {
    val left:  Decoder[Either[A, B]]= a.map(Left.apply)
    val right: Decoder[Either[A, B]]= b.map(Right.apply)
    left or right
  }
}
