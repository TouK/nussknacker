package pl.touk.nussknacker.engine.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter

object subprocess {

  //TODO: outputVariableNames has to be optional because of backward compatibility (nodes from db are converted to this..), remove optional in next version
  @JsonCodec case class SubprocessRef(id: String, parameters: List[Parameter], outputVariableNames: Option[Map[String, String]] = None)

}
