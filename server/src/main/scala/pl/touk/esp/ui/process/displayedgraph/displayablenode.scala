package pl.touk.esp.ui.process.displayedgraph

import pl.touk.esp.engine.graph.evaluatedparam.Parameter
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.sink.SinkRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.engine.graph.variable.Field

object displayablenode {

  sealed trait DisplayableNode {
    def id: String
  }

  case class Source(id: String, ref: SourceRef) extends DisplayableNode

  case class Sink(id: String, ref: SinkRef, endResult: Option[Expression]) extends DisplayableNode

  case class VariableBuilder(id: String, varName: String, fields: List[Field]) extends DisplayableNode

  case class Processor(id: String, service: ServiceRef) extends DisplayableNode

  case class Enricher(id: String, service: ServiceRef, output: String) extends DisplayableNode

  case class Filter(id: String, expression: Expression) extends DisplayableNode

  case class Switch(id: String, expression: Expression, exprVal: String) extends DisplayableNode

  case class Aggregate(id: String, aggregatedVar: String,
                       keyExpression: Expression, durationInMillis: Long,
                       slideInMillis: Long,
                       triggerExpression: Option[Expression],
                       foldingFunRef: Option[String]) extends DisplayableNode

  case class CustomNode(id: String, outputVar: String, customNodeRef: String, parameters: List[Parameter]) extends DisplayableNode

  case class Edge(from: String, to: String, label: Option[Expression])

}
