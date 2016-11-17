package pl.touk.esp.engine.graph

import pl.touk.esp.engine.graph.evaluatedparam.Parameter
import sink.SinkRef
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.engine.graph.variable.Field

object node {

  sealed trait Node {
    def data: NodeData
    def id: String = data.id
  }

  sealed trait OneOutputNode extends Node {
    def next: SubsequentNode
  }

  case class SourceNode(data: Source, next: SubsequentNode) extends OneOutputNode

  sealed trait SubsequentNode extends Node

  case class OneOutputSubsequentNode(data: OneOutputSubsequentNodeData, next: SubsequentNode) extends OneOutputNode with SubsequentNode

  case class FilterNode(data: Filter, nextTrue: SubsequentNode, nextFalse: Option[SubsequentNode] = None) extends SubsequentNode

  case class SwitchNode(data: Switch, nexts: List[Case], defaultNext: Option[SubsequentNode] = None) extends SubsequentNode

  case class SplitNode(data: Split, nextParts: List[SubsequentNode]) extends SubsequentNode

  case class Case(expression: Expression, node: SubsequentNode)

  case class EndingNode(data: EndingNodeData) extends SubsequentNode

  sealed trait NodeData {
    def id: String
  }

  case class Source(id: String, ref: SourceRef) extends NodeData

  case class Filter(id: String, expression: Expression) extends NodeData

  case class Switch(id: String, expression: Expression, exprVal: String) extends NodeData

  sealed trait OneOutputSubsequentNodeData extends NodeData

  case class VariableBuilder(id: String, varName: String, fields: List[Field]) extends OneOutputSubsequentNodeData

  case class Variable(id: String, varName: String, value: Expression) extends OneOutputSubsequentNodeData

  case class Enricher(id: String, service: ServiceRef, output: String) extends OneOutputSubsequentNodeData

  case class CustomNode(id: String, outputVar: String, nodeType: String, parameters: List[Parameter]) extends OneOutputSubsequentNodeData

  case class Split(id: String) extends NodeData

  sealed trait EndingNodeData extends NodeData

  case class Processor(id: String, service: ServiceRef) extends OneOutputSubsequentNodeData with EndingNodeData

  case class Sink(id: String, ref: SinkRef, endResult: Option[Expression] = None) extends EndingNodeData

}
