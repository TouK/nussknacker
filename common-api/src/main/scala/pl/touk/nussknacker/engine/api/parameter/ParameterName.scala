package pl.touk.nussknacker.engine.api.parameter

import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class ParameterName(value: String) {
  def withBranchId(branchId: String): ParameterName = ParameterName(s"$value for branch $branchId")
}

object ParameterName {
  implicit val encoder: Encoder[ParameterName] = deriveUnwrappedEncoder
  implicit val decoder: Decoder[ParameterName] = deriveUnwrappedDecoder

  implicit val keyEncoder: KeyEncoder[ParameterName] = KeyEncoder.encodeKeyString.contramap(_.value)
  implicit val keyDecoder: KeyDecoder[ParameterName] = KeyDecoder.decodeKeyString.map(ParameterName(_))
}
