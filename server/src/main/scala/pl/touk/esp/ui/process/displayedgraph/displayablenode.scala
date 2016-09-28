package pl.touk.esp.ui.process.displayedgraph

import pl.touk.esp.engine.graph.expression.Expression

object displayablenode {

  case class Edge(from: String, to: String, label: Option[Expression])

}
