package pl.touk.nussknacker.engine.api.process

import io.circe.{Decoder, Encoder}

import scala.util.{Failure, Success, Try}

final case class ProcessId(value: Long)

object ProcessId {
  implicit val encoder: Encoder[ProcessId] = Encoder.encodeLong.contramap(_.value)
  implicit val decoder: Decoder[ProcessId] = Decoder.decodeLong.map(ProcessId(_))

  def apply(value: String): ProcessId = Try(value.toLong) match {
    case Success(id) => ProcessId(id)
    case Failure(_) => throw new IllegalArgumentException(s"Value '$value' is not valid ProcessId.")
  }
}
