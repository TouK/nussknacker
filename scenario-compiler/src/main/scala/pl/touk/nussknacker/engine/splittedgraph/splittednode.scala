package pl.touk.nussknacker.engine.splittedgraph

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node._

object splittednode {

  sealed trait SplittedNode[+T <: NodeData] {
    def data: T
    def id: NodeId = data.id
    def isEnding: Boolean
  }

  sealed trait OneOutputNode[+T <: NodeData] extends SplittedNode[T] {
    def next: Option[Next]
  }

  case class SourceNode[+T <: StartingNodeData](data: T, next: Option[Next]) extends OneOutputNode[T] {
    override def isEnding: Boolean = next.isEmpty
  }

  sealed trait SubsequentNode[T <: NodeData] extends SplittedNode[T]

  case class OneOutputSubsequentNode[T <: OneOutputSubsequentNodeData](data: T, next: Option[Next])
      extends OneOutputNode[T]
      with SubsequentNode[T] {
    override def isEnding: Boolean = next.isEmpty
  }

  case class SplitNode(data: Split, nexts: List[Next]) extends SubsequentNode[Split] {
    override def isEnding: Boolean = nexts.isEmpty
  }

  case class FilterNode(data: Filter, nextTrue: Option[Next], nextFalse: Option[Next] = None)
      extends SubsequentNode[Filter] {
    override def isEnding: Boolean = nextTrue.isEmpty && nextFalse.isEmpty
  }

  case class SwitchNode(data: Switch, nexts: List[Case], defaultNext: Option[Next] = None)
      extends SubsequentNode[Switch] {
    override def isEnding: Boolean = nexts.forall(_.node.isEmpty) && defaultNext.isEmpty
  }

  case class Case(expression: Expression, node: Option[Next])

  case class EndingNode[T <: EndingNodeData](data: T) extends SubsequentNode[T] {
    override def isEnding: Boolean = true
  }

  sealed trait Next {
    def id: NodeId
  }

  case class NextNode(node: SubsequentNode[_ <: NodeData]) extends Next {
    def id: NodeId = node.id
  }

  case class PartRef(id: NodeId) extends Next

}
