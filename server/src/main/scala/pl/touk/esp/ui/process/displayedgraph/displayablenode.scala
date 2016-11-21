package pl.touk.esp.ui.process.displayedgraph

import pl.touk.esp.engine.api.UserDefinedProcessAdditionalFields
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node

object displayablenode {

  case class Edge(from: String, to: String, label: Option[Expression])
  case class NodeAdditionalFields(description: Option[String]) extends node.UserDefinedAdditionalNodeFields
  case class ProcessAdditionalFields(description: Option[String]) extends UserDefinedProcessAdditionalFields
}
