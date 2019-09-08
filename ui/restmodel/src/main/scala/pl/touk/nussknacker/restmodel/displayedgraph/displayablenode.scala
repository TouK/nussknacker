package pl.touk.nussknacker.restmodel.displayedgraph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node

object displayablenode {

  @JsonCodec sealed abstract class EdgeType
  object EdgeType {
    sealed trait FilterEdge extends EdgeType
    sealed trait SwitchEdge extends EdgeType
    case object FilterTrue extends FilterEdge
    case object FilterFalse extends FilterEdge
    case class NextSwitch(condition: Expression) extends SwitchEdge
    case object SwitchDefault extends SwitchEdge
    case class SubprocessOutput(name: String) extends EdgeType
  }

  case class Edge(from: String, to: String, edgeType: Option[EdgeType])
  case class NodeAdditionalFields(description: Option[String]) extends node.UserDefinedAdditionalNodeFields
}
