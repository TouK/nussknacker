package pl.touk.nussknacker.engine.api.process

import io.circe.{Decoder, Encoder}

import scala.util.{Failure, Success, Try}

// It is a synthetic, autoincremental id of scenario in the database.
// TODO: We should avoid exposing it anywhere, especially in the API as it is not safety to pass anywhere such an information.
// TODO: Also we'd rather don't need a synthetic identifier, ProcessName (which meant to be a human friendly id) would be sufficient
final case class ProcessId(value: Long) {
  override def toString: String = value.toString
}

object ProcessId {
  implicit val encoder: Encoder[ProcessId] = Encoder.encodeLong.contramap(_.value)
  implicit val decoder: Decoder[ProcessId] = Decoder.decodeLong.map(ProcessId(_))

  def apply(value: String): ProcessId = Try(value.toLong) match {
    case Success(id) => ProcessId(id)
    case Failure(_)  => throw new IllegalArgumentException(s"Value '$value' is not valid ProcessId.")
  }

}
