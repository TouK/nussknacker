package pl.touk.nussknacker.ui.security.oauth2

import io.circe._

import scala.concurrent.duration.{Deadline, FiniteDuration, SECONDS}

protected[oauth2] trait EpochSecondsCodecs {
  import cats.syntax.either._
  implicit val decodeDeadline: Decoder[Deadline] = new Decoder[Deadline] {
    def apply(c: HCursor): Decoder.Result[Deadline] = {
      decodeFiniteDuration(c).map(Deadline(_))
    }
  }

  implicit val encodeDeadline: Encoder[Deadline] = new Encoder[Deadline] {
    def apply(value: Deadline): Json = Json.fromLong(value.time.toSeconds)
  }

  implicit val decodeFiniteDuration: Decoder[FiniteDuration] = new Decoder[FiniteDuration] {
    def apply(c: HCursor): Decoder.Result[FiniteDuration] = {
      c.value.asNumber.flatMap(_.toLong)
        .map(FiniteDuration(_, SECONDS))
        .map(Right(_))
        .getOrElse(Left(DecodingFailure("FiniteDuration", c.history)))
    }
  }

  implicit val encodeFiniteDuration: Encoder[FiniteDuration] = new Encoder[FiniteDuration] {
    def apply(value: FiniteDuration): Json = Json.fromLong(value.toSeconds)
  }
}
