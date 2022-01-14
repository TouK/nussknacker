package pl.touk.nussknacker.engine.api.deployment

import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.CirceUtil

object GraphProcess {

  implicit val encoder: Encoder[GraphProcess] = Encoder.encodeJson.contramap(_.json)
  implicit val decoder: Decoder[GraphProcess] = Decoder.decodeJson.map(GraphProcess(_))

  val empty: GraphProcess = GraphProcess("{}")

  //TODO: Check does json contain proper Canonical form
  def apply(jsonString: String): GraphProcess = {
    val json = CirceUtil.decodeJsonUnsafe[Json](jsonString, "invalid graph process json string")
    new GraphProcess(json)
  }

}

case class GraphProcess(json: Json) extends AnyVal {
  //TODO: Use GraphProcess instead of toString method
  override def toString: String = json.spaces2
}
