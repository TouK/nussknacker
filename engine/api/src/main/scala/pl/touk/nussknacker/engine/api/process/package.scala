package pl.touk.nussknacker.engine.api

import argonaut.Argonaut._
import argonaut.{CodecJson, _}
import io.circe.{Decoder, Encoder}

package object process {

  object ProcessName {
    implicit val encoder: Encoder[ProcessName] = Encoder.encodeString.contramap(_.value)
    implicit val decoder: Decoder[ProcessName] = Decoder.decodeString.map(ProcessName(_))

    // argonaut codec for backward compatibility
    implicit val codec: CodecJson[ProcessName] = CodecJson.derived[String].xmap(ProcessName(_))(_.value)
  }

  final case class ProcessName(value: String) extends AnyVal
}
