package pl.touk.nussknacker.engine.api

import io.circe
import io.circe.Decoder
import io.circe.generic.extras.Configuration

object CirceUtil {

  implicit val configuration: Configuration = Configuration
    .default
    .withDefaults
    .withDiscriminator("type")

  def decodeJson[T:Decoder](json: String): Either[circe.Error, T]
    = io.circe.parser.parse(json).right.flatMap(Decoder[T].decodeJson)

}
