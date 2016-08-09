package pl.touk.esp.engine.canonicalgraph

import pl.touk.esp.engine.graph.sink.SinkRef
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.engine.graph.variable.Field

// This package can't be inside marshall package because of format derivation for types using shapelesss
// Don't change class names - they are putted in the field 'type'
object canonicalnode {

  sealed trait CanonicalNode {
    def id: String
  }

  case class Source(id: String, ref: SourceRef) extends CanonicalNode

  case class Sink(id: String, ref: SinkRef, endResult: Option[Expression]) extends CanonicalNode

  case class VariableBuilder(id: String, varName: String, fields: List[Field]) extends CanonicalNode

  case class Processor(id: String, service: ServiceRef) extends CanonicalNode

  case class Enricher(id: String, service: ServiceRef, output: String) extends CanonicalNode

  case class Filter(id: String, expression: Expression, nextFalse: List[CanonicalNode]) extends CanonicalNode

  case class Switch(id: String, expression: Expression, exprVal: String,
                    nexts: List[Case], defaultNext: List[CanonicalNode]) extends CanonicalNode

  case class Case(expression: Expression, nodes: List[CanonicalNode])

  case class Aggregate(id: String, aggregatedVar: String,
                       keyExpression: Expression, durationInMillis: Long,
                       slideInMillis: Long,
                       triggerExpression: Option[Expression],
                       foldingFunRef: Option[String]) extends CanonicalNode

}
