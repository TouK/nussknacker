package pl.touk.nussknacker.engine.splittedgraph

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.graph.node.{
  EndingNode => _,
  OneOutputSubsequentNode => _,
  SourceNode => _,
  SubsequentNode => _,
  _
}
import pl.touk.nussknacker.engine.splittedgraph.end.End
import pl.touk.nussknacker.engine.splittedgraph.splittednode._

object part {

  sealed trait ProcessPart {
    type T <: NodeData
    def node: SplittedNode[T]
    def id: String = node.id
    def nextParts: List[SubsequentPart]
    def ends: List[End]
  }

  case class SourcePart(node: SourceNode[StartingNodeData], nextParts: List[SubsequentPart], ends: List[End])
      extends ProcessPart {
    override type T = StartingNodeData
  }

  sealed trait SubsequentPart extends ProcessPart

  sealed trait CustomNodePart extends SubsequentPart {
    override type T = CustomNode
  }

  /**
    * The anonymous continuation is embedded in `node` (the `SourcePart` pattern); an `EndingNode` means the node ends
    * the scenario. A component that declares additional outputs can still arrive in this shape through a legacy or
    * API-built graph with an unnamed edge.
    */
  case class SingleOutputCustomNodePart(
      node: SubsequentNode[CustomNode],
      nextParts: List[SubsequentPart],
      ends: List[End]
  ) extends CustomNodePart

  /**
    * Every wired continuation, the main one included, under its output name; an unwired main has no entry (which name
    * is the main one only the compiler knows, so it owns the DeadEnd synthesis). "Multi" refers to the named-edge
    * wiring form (a canonical `CustomNodeWithOutputs`), not the entry count: a node with a single wired output still
    * takes this shape.
    *
    * The spine is an `EndingNode` only because the continuations live in `outputs` instead - its `isEnding` is
    * meaningless here (this part has wired outputs by definition), so the part's shape, not the node's, is what
    * decides whether the custom node ends the scenario.
    */
  case class MultiOutputCustomNodePart(
      node: EndingNode[CustomNode],
      outputs: NonEmptyList[SplittedOutput]
  ) extends CustomNodePart {

    override def nextParts: List[SubsequentPart] =
      outputs.toList.flatMap(_.nextParts)

    override def ends: List[End] =
      outputs.toList.flatMap(_.ends)

  }

  case class SplittedOutput(name: String, next: Option[Next], nextParts: List[SubsequentPart], ends: List[End])

  case class SinkPart(node: EndingNode[Sink]) extends SubsequentPart {
    override type T = Sink

    override def nextParts: List[SubsequentPart] = List.empty
    override def ends: List[End]                 = List.empty
  }

}
