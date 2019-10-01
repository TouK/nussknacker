package pl.touk.nussknacker.engine.api

import io.circe.{Decoder, Encoder}

package object process {

  object ProcessName {
    implicit val encoder: Encoder[ProcessName] = Encoder.encodeString.contramap(_.value)
    implicit val decoder: Decoder[ProcessName] = Decoder.decodeString.map(ProcessName(_))
  }

  final case class ProcessName(value: String) extends AnyVal
}
