package pl.touk.esp.ui.process.displayedgraph

import pl.touk.esp.engine.api.UserDefinedProcessAdditionalFields
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node

object displayablenode {

  sealed abstract class EdgeType
  object EdgeType {
    sealed trait FilterEdge extends EdgeType
    sealed trait SwitchEdge extends EdgeType
    case object FilterTrue extends FilterEdge
    case object FilterFalse extends FilterEdge
    case class NextSwitch(condition: Expression) extends SwitchEdge
    case object SwitchDefault extends SwitchEdge
  }

  case class Edge(from: String, to: String, edgeType: Option[EdgeType])
  case class NodeAdditionalFields(description: Option[String]) extends node.UserDefinedAdditionalNodeFields
  case class ProcessAdditionalFields(description: Option[String], groups: Set[Set[String]] = Set()) extends UserDefinedProcessAdditionalFields
}
