package pl.touk.nussknacker.engine.api.deployment

import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.CirceUtil

object GraphProcess {

  implicit val encoder: Encoder[GraphProcess] = Encoder.encodeJson.contramap(_.json)
  implicit val decoder: Decoder[GraphProcess] = Decoder.decodeJson.map(GraphProcess(_))

  val empty: GraphProcess = GraphProcess("{}")

  def apply(jsonString: String): GraphProcess = {
    val json = CirceUtil.decodeJsonUnsafe[Json](jsonString, "invalid graph process json string")
    new GraphProcess(json)
  }

}

//TODO: Consider replace Json by CanonicalProcess when it can be possible or use CanonicalProcess instead of GraphProcess?
final case class GraphProcess(json: Json) {
  def marshall: String = json.spaces2
}
