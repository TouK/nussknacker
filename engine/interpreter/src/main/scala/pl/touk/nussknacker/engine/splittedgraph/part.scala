package pl.touk.nussknacker.engine.splittedgraph

import pl.touk.nussknacker.engine.graph.node.{OneOutputSubsequentNode => _, EndingNode => _, SourceNode => _, _}
import pl.touk.nussknacker.engine.splittedgraph.end.{End, NormalEnd}
import pl.touk.nussknacker.engine.splittedgraph.splittednode._

object part {

  sealed trait ProcessPart {
    type T <: NodeData
    def node: SplittedNode[T]
    def id: String = node.id
  }

  case class SourcePart(node: SourceNode, nextParts: List[SubsequentPart], ends: List[End]) extends ProcessPart {
    override type T = StartingNodeData
  }

  sealed trait SubsequentPart extends ProcessPart

  case class CustomNodePart(node: OneOutputSubsequentNode[CustomNode], nextParts: List[SubsequentPart], ends: List[End]) extends SubsequentPart {
    override type T = CustomNode
  }

  case class SplitPart(node: splittednode.SplitNode) extends SubsequentPart {
    override type T = Split
  }

  case class SinkPart(node: EndingNode[Sink]) extends SubsequentPart {
    override type T = Sink

  }

}