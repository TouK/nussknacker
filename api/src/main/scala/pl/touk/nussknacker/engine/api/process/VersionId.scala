package pl.touk.nussknacker.engine.api.process

import io.circe.{Decoder, Encoder}

final case class VersionId(value: Long) {
  def increase: VersionId = VersionId(value + 1)
}

object VersionId {

  val initialVersionId: VersionId = VersionId(0)

  implicit val encoder: Encoder[VersionId] = Encoder.encodeLong.contramap(_.value)
  implicit val decoder: Decoder[VersionId] = Decoder.decodeLong.map(VersionId(_))
}
