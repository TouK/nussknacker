package pl.touk.nussknacker.engine.api.process

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

// This class is a mostly used as a human-friendly identifier - something like a slug.
// For a legacy reasons it is called Name. Probable because of collision with ProcessId (see docs there)
// TODO: Rename to ScenarioSlug / ScenarioId and add validations that it is a proper url path part
// TODO: Avoid using it as a easy-to-change label. If we really need such a thing, we should introduce a separate ScenarioLabel class
final case class ProcessName(value: String) {
  override def toString: String = value
}

object ProcessName {
  implicit val encoder: Encoder[ProcessName] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[ProcessName] = Decoder.decodeString.map(ProcessName(_))

  implicit val keyEncoder: KeyEncoder[ProcessName] = KeyEncoder.encodeKeyString.contramap(_.value)
  implicit val keyDecoder: KeyDecoder[ProcessName] = KeyDecoder.decodeKeyString.map(ProcessName(_))
}
