package pl.touk.nussknacker.engine.deployment

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class EngineSetupName(value: String) {
  def withSuffix(suffix: String): EngineSetupName = EngineSetupName(value + suffix)

  override def toString: String = value
}

object EngineSetupName {
  implicit val encoder: Encoder[EngineSetupName] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[EngineSetupName] = Decoder.decodeString.map(EngineSetupName(_))

  implicit val keyEncoder: KeyEncoder[EngineSetupName] = KeyEncoder.encodeKeyString.contramap(_.value)
  implicit val keyDecoder: KeyDecoder[EngineSetupName] = KeyDecoder.decodeKeyString.map(EngineSetupName(_))
}
