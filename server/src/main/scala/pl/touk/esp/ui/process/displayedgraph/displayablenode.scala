package pl.touk.esp.ui.process.displayedgraph

import pl.touk.esp.engine.api.UserDefinedProcessAdditionalFields
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node

object displayablenode {

  case class Edge(from: String, to: String, label: Option[String])
  case class NodeAdditionalFields(description: Option[String]) extends node.UserDefinedAdditionalNodeFields
  case class ProcessAdditionalFields(description: Option[String], groups: Set[Set[String]] = Set()) extends UserDefinedProcessAdditionalFields
}
