package pl.touk.nussknacker.engine.graph

import io.circe.Decoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import sttp.tapir.Schema

object fragment {

  // we decode absence to empty map because of backwards compatibility (objects in db can contain no `outputVariableNames` field)
  private implicit val mapWithFallbackToEmptyMapDecoder: Decoder[Map[String, String]] =
    Decoder.decodeOption[Map[String, String]](Decoder.decodeMap[String, String]).map(_.getOrElse(Map.empty))

  @JsonCodec case class FragmentRef(
      id: String,
      parameters: List[NodeParameter],
      outputVariableNames: Map[String, String] = Map.empty
  )

  object FragmentRef {
    implicit val schema: Schema[FragmentRef] = Schema.derived[FragmentRef]
  }

}
