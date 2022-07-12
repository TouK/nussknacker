package pl.touk.nussknacker.ui.security.oauth2

import io.circe._

import java.time.Instant
import scala.concurrent.duration.{Deadline, FiniteDuration, SECONDS}

protected[oauth2] trait RelativeSecondsCodecs {
  import cats.syntax.either._
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

protected[oauth2] trait EpochSecondsCodecs {
  implicit val instantEncoder: Encoder[Instant] = Encoder.encodeLong.contramap(_.getEpochSecond)
  implicit val instantDecoder: Decoder[Instant] = Decoder.decodeLong.map(Instant.ofEpochSecond)
}

