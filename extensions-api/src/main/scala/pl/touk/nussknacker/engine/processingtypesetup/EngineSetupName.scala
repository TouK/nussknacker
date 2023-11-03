package pl.touk.nussknacker.engine.processingtypesetup

import io.circe.{Decoder, Encoder}

final case class EngineSetupName(value: String) {
  override def toString: String = value
}

object EngineSetupName {
  implicit val encoder: Encoder[EngineSetupName] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[EngineSetupName] = Decoder.decodeString.map(EngineSetupName(_))
}
