package pl.touk.esp.engine.splittedgraph

import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.variable.Field

object splittednode {

  sealed trait SplittedNode {
    def id: String
  }

  case class Source(id: String, next: Next) extends SplittedNode

  case class Sink(id: String, endResult: Option[Expression] = None) extends SplittedNode

  case class VariableBuilder(id: String, varName: String, fields: List[Field], next: Next) extends SplittedNode

  case class Processor(id: String, service: ServiceRef, next: Next) extends SplittedNode

  case class Enricher(id: String, service: ServiceRef, output: String, next: Next) extends SplittedNode

  case class Filter(id: String, expression: Expression, nextTrue: Next,
                    nextFalse: Option[Next] = None) extends SplittedNode

  case class Switch(id: String, expression: Expression, exprVal: String,
                    nexts: List[Case], defaultNext: Option[Next] = None) extends SplittedNode

  case class Case(expression: Expression, node: Next)

  case class Aggregate(id: String, keyExpression: Expression, next: PartRef) extends SplittedNode

  sealed trait Next
  case class NextNode(node: SplittedNode) extends Next
  case class PartRef(id: String) extends Next

}