package pl.touk.nussknacker.engine.util.json

import io.circe._

trait EitherCodecs {
  implicit def eitherDecoder[A, B](implicit a: Decoder[A], b: Decoder[B]): Decoder[Either[A, B]] = {
    val left: Decoder[Either[A, B]] = a.map(Left.apply)
    val right: Decoder[Either[A, B]] = b.map(Right.apply)
    left or right
  }

  implicit def eitherEncoder[A, B](implicit a: Encoder[A], b: Encoder[B]): Encoder[Either[A, B]] = {
    case Left(value) => a.apply(value)
    case Right(value) => b.apply(value)
  }
}

object EitherCodecs extends EitherCodecs