package pl.touk.esp.engine.graph

import pl.touk.esp.engine.api.sink.SinkRef
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.engine.graph.variable.Field

object node {

  sealed trait Node {
    def id: String
  }

  case class Source(id: String, ref: SourceRef, next: Node) extends Node

  case class Sink(id: String, ref: SinkRef, endResult: Option[Expression] = None) extends Node

  case class VariableBuilder(id: String, varName: String, fields: List[Field], next: Node) extends Node

  case class Processor(id: String, service: ServiceRef, next: Node) extends Node

  case class Enricher(id: String, service: ServiceRef, output: String, next: Node) extends Node

  case class Filter(id: String, expression: Expression, nextTrue: Node,
                    nextFalse: Option[Node] = None) extends Node

  case class Switch(id: String, expression: Expression, exprVal: String,
                    nexts: List[Case], defaultNext: Option[Node] = None) extends Node

  case class Case(expression: Expression, node: Node)

  case class Aggregate(id: String, aggregatedVar: String,
                       keyExpression: Expression, durationInMillis: Long,
                       stepInMillis: Long, next: Node) extends Node

}
