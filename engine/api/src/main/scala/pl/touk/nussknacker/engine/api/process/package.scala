package pl.touk.nussknacker.engine.api

import io.circe.{Decoder, Encoder}

package object process {
  object ProcessName {
    implicit val encoder: Encoder[ProcessName] = Encoder.encodeString.contramap(_.value)
    implicit val decoder: Decoder[ProcessName] = Decoder.decodeString.map(ProcessName(_))
  }

  final case class ProcessName(value: String) extends AnyVal

  object ProcessId {
    implicit val encoder: Encoder[ProcessId] = Encoder.encodeLong.contramap(_.value)
    implicit val decoder: Decoder[ProcessId] = Decoder.decodeLong.map(ProcessId(_))

    def apply(value: Long): ProcessId = new ProcessId(value)
    def apply(value: String): ProcessId = new ProcessId(Integer.valueOf(value).toLong)
  }

  final case class ProcessId(value: Long) extends AnyVal
}
