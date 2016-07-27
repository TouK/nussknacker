package pl.touk.esp.engine.flatgraph

import pl.touk.esp.engine.api.sink.SinkRef
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.engine.graph.variable.Field

// This package can't be inside marshall package because of format derivation for types using shapelesss
// Don't change class names - they are putted in the field 'type'
object flatnode {

  sealed trait FlatNode {
    def id: String
  }

  case class Source(id: String, ref: SourceRef) extends FlatNode

  case class Sink(id: String, ref: SinkRef, endResult: Option[Expression]) extends FlatNode

  case class VariableBuilder(id: String, varName: String, fields: List[Field]) extends FlatNode

  case class Processor(id: String, service: ServiceRef) extends FlatNode

  case class Enricher(id: String, service: ServiceRef, output: String) extends FlatNode

  case class Filter(id: String, expression: Expression, nextFalse: List[FlatNode]) extends FlatNode

  case class Switch(id: String, expression: Expression, exprVal: String,
                    nexts: List[Case], defaultNext: List[FlatNode]) extends FlatNode

  case class Case(expression: Expression, nodes: List[FlatNode])

}
