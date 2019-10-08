package pl.touk.nussknacker.engine.api

import java.nio.charset.StandardCharsets

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

  def decodeJson[T:Decoder](json: Array[Byte]): Either[circe.Error, T] = decodeJson(new String(json, StandardCharsets.UTF_8))

  def decodeJsonUnsafe[T:Decoder](json: String): T = decodeJson(json).right.get

  def decodeJsonUnsafe[T:Decoder](json: Array[Byte]): T = decodeJson(json).right.get

}
