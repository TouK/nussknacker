package pl.touk.nussknacker.engine.api.deployment

import io.circe.parser.parse
import io.circe.{Decoder, Encoder, Json}

object GraphProcess {

  implicit val encoder: Encoder[GraphProcess] = Encoder.encodeJson.contramap(_.json)
  implicit val decoder: Decoder[GraphProcess] = Decoder.decodeJson.map(GraphProcess(_))

  val empty: GraphProcess = GraphProcess("{}")

  //TODO: Check does json contain proper Canonical form
  def apply(jsonString: String): GraphProcess = {
    val json = parse(jsonString) match {
      case Left(_) => throw new IllegalArgumentException(s"Invalid raw json string: $jsonString.")
      case Right(json) => json
    }

    new GraphProcess(json)
  }

}

case class GraphProcess(json: Json) extends AnyVal {
  override def toString: String = json.spaces2
}
