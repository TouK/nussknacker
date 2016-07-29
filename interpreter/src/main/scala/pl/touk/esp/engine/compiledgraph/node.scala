package pl.touk.esp.engine.compiledgraph

import pl.touk.esp.engine.api.sink.SinkRef
import pl.touk.esp.engine.compiledgraph.expression.Expression
import pl.touk.esp.engine.compiledgraph.service.ServiceRef
import pl.touk.esp.engine.compiledgraph.variable.Field
import pl.touk.esp.engine.graph.source.SourceRef

object node {

  sealed trait Node {
    def id: String
  }

  case class Source(id: String, ref: SourceRef, next: Node) extends Node

  case class Sink(id: String, ref: SinkRef, endResult: Option[Expression]) extends Node

  case class VariableBuilder(id: String, varName: String, fields: List[Field], next: Node) extends Node

  case class Processor(id: String, service: ServiceRef, next: Node) extends Node

  case class Enricher(id: String, service: ServiceRef, output: String, next: Node) extends Node

  case class Filter(id: String, expression: Expression, nextTrue: Node,
                    nextFalse: Option[Node]) extends Node

  case class Switch(id: String, expression: Expression, exprVal: String,
                    nexts: List[Case], defaultNext: Option[Node]) extends Node

  case class Case(expression: Expression, node: Node)

}
