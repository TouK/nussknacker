package pl.touk.esp.engine.uibackend

import pl.touk.esp.engine.api.sink.SinkRef
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.engine.graph.variable.Field

object GraphDisplay {

  sealed trait FlatNode {
    def id: String
  }

  case class Source(id: String, ref: SourceRef) extends FlatNode

  case class Sink(id: String, ref: SinkRef, endResult: Option[Expression]) extends FlatNode

  case class VariableBuilder(id: String, varName: String, fields: List[Field]) extends FlatNode

  case class Processor(id: String, service: ServiceRef) extends FlatNode

  case class Enricher(id: String, service: ServiceRef, output: String) extends FlatNode

  case class Filter(id: String, expression: Expression) extends FlatNode

  case class Switch(id: String, expression: Expression, exprVal: String) extends FlatNode

  case class Edge(from: String, to: String, label: Option[Expression])

  case class Graph(nodes: List[GraphDisplay.FlatNode], edges: List[Edge])
}
