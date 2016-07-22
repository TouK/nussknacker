package pl.touk.esp.engine.graph

import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.variable.Field

import scala.language.implicitConversions

object node {

  sealed trait Node {
    def id: String
  }

  case class StartNode(id: String, next: Node) extends Node

  case class End(id: String, endResult: Option[Expression] = None) extends Node

  case class VariableBuilder(id: String, varName: String, fields: Seq[Field], next: Node) extends Node

  case class Processor(id: String, processor: ServiceRef, next: Node) extends Node

  case class Enricher(id: String, processor: ServiceRef, output: String, next: Node) extends Node

  case class Filter(id: String, expression: Expression, nextTrue: Node,
                    nextFalse: Option[Node] = None) extends Node

  case class Switch(id: String, expression: Expression,
                    exprVal: String, nexts: List[(Expression, Node)],
                    defaultNext: Option[Node] = None) extends Node

}
