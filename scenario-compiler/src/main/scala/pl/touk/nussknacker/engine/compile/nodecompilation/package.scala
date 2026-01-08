package pl.touk.nussknacker.engine.compile

import pl.touk.nussknacker.engine.graph.EdgeType

package object nodecompilation {
  case class OutgoingEdge(target: String, edgeType: Option[EdgeType])
}
