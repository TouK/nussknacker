package pl.touk.nussknacker.restmodel.displayedgraph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.EdgeType

object displayablenode {

  import pl.touk.nussknacker.engine.api.CirceUtil._

  @JsonCodec case class Edge(from: String, to: String, edgeType: Option[EdgeType])
}
