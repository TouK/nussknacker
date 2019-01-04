package pl.touk.nussknacker.restmodel.displayedgraph

import pl.touk.nussknacker.engine.api.UserDefinedProcessAdditionalFields
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node

object displayablenode {

  sealed abstract class EdgeType
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
  case class ProcessAdditionalFields(description: Option[String],
                                     groups: Set[Group] = Set(),
                                     properties: Map[String, String] = Map.empty) extends UserDefinedProcessAdditionalFields

  case class Group(id: String, nodes: Set[String])

}
