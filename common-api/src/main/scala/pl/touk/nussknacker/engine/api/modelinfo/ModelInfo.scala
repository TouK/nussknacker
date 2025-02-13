package pl.touk.nussknacker.engine.api.modelinfo

import io.circe
import io.circe.parser._
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, Printer}

case class ModelInfo private (private val value: Map[String, String]) {

  val asJsonString: String = {
    val prettyParams = Printer.spaces2.copy(sortKeys = true)
    value.asJson.printWith(prettyParams)
  }

}

object ModelInfo {

  val empty = new ModelInfo(Map.empty[String, String])

  def fromMap(map: Map[String, String]) = new ModelInfo(map)

  def parseJsonString(json: String): Either[circe.Error, ModelInfo] = {
    parse(json)
      .flatMap(js => Decoder[Map[String, String]].decodeJson(js))
      .map(new ModelInfo(_))
  }

  implicit val encoder: Encoder[ModelInfo] = Encoder[Map[String, String]].contramap(_.value)

  implicit val decoder: Decoder[ModelInfo] = Decoder[Map[String, String]].map(ModelInfo.fromMap)

}
