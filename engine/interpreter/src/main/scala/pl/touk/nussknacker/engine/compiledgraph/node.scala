package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.compiledgraph.expression.Expression
import pl.touk.nussknacker.engine.compiledgraph.service.ServiceRef
import pl.touk.nussknacker.engine.compiledgraph.variable.Field

object node {

  sealed trait Node {
    def id: String
  }

  case class Source(id: String, next: Next) extends Node

  case class Sink(id: String, ref: String, endResult: Option[Expression]) extends Node

  case class VariableBuilder(id: String, varName: String, value: Either[Expression, List[Field]], next: Next) extends Node

  case class Processor(id: String, service: ServiceRef, next: Next, isDisabled: Boolean) extends Node

  case class EndingProcessor(id: String, service: ServiceRef, isDisabled: Boolean) extends Node

  case class Enricher(id: String, service: ServiceRef, output: String, next: Next) extends Node

  case class Filter(id: String, expression: Expression, nextTrue: Next,
                    nextFalse: Option[Next], isDisabled: Boolean) extends Node

  case class Switch(id: String, expression: Expression, exprVal: String,
                    nexts: List[Case], defaultNext: Option[Next]) extends Node

  case class Case(expression: Expression, node: Next)

  case class CustomNode(id:String, params:List[Parameter],  next: Next) extends Node

  case class SubprocessStart(id: String, params: List[Parameter], next: Next) extends Node

  case class SubprocessEnd(id: String, next: Next) extends Node

  case class SplitNode(id: String) extends Node

  sealed trait Next {
    def id: String
  }
  case class NextNode(node: Node) extends Next {
    def id = node.id
  }
  case class PartRef(id: String) extends Next

}