package pl.touk.nussknacker.engine.splittedgraph

import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.split.ProcessSplitter.NextWithParts

object splittednode {

  sealed trait SplittedNode[+T <: NodeData] {
    def data: T
    def id: String = data.id
  }

  sealed trait OneOutputNode[+T <: NodeData] extends SplittedNode[T] {
    def next: Next
  }

  case class SourceNode[+T<: StartingNodeData](data: T, next: Next) extends OneOutputNode[T]

  sealed trait SubsequentNode[T <: NodeData] extends SplittedNode[T]

  case class OneOutputSubsequentNode[T <: OneOutputSubsequentNodeData](data: T, next: Next) extends OneOutputNode[T] with SubsequentNode[T]

  case class SplitNode(data: Split, nexts: List[Next]) extends SubsequentNode[Split]

  case class FilterNode(data: Filter, nextTrue: Next, nextFalse: Option[Next] = None) extends SubsequentNode[Filter]

  case class SwitchNode(data: Switch, nexts: List[Case], defaultNext: Option[Next] = None) extends SubsequentNode[Switch]

  case class Case(expression: Expression, node: Next)

  case class EndingNode[T <: EndingNodeData](data: T) extends SubsequentNode[T]

  sealed trait Next {
    def id: String
  }
  case class NextNode(node: SubsequentNode[_ <: NodeData]) extends Next {
    def id = node.id
  }
  case class PartRef(id: String) extends Next

}
