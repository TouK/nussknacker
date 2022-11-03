package pl.touk.nussknacker.engine.graph

import io.circe.Decoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter

object subprocess {

  //we decode absence to empty map because of backwards compatibility (objects in db can contain no `outputVariableNames` field)
  private implicit val mapWithFallbackToEmptyMapDecoder: Decoder[Map[String, String]] =
    Decoder.decodeOption[Map[String, String]](Decoder.decodeMap[String, String]).map(_.getOrElse(Map.empty))

  @JsonCodec case class SubprocessRef(id: String, parameters: List[Parameter], outputVariableNames: Map[String, String] = Map.empty)
}
