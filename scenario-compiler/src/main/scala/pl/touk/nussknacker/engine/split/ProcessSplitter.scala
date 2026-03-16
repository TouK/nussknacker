package pl.touk.nussknacker.engine.split

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.splittedgraph._
import pl.touk.nussknacker.engine.splittedgraph.end.{DeadEnd, End, NormalEnd}
import pl.touk.nussknacker.engine.splittedgraph.part._
import pl.touk.nussknacker.engine.splittedgraph.splittednode.{NextNode, PartRef}

//NOTE: logic of splitter should match logic in SplittedNodesCollector
object ProcessSplitter {

  def split(process: EspProcess): SplittedProcess = {
    SplittedProcess(process.metaData, process.roots.map(split))
  }

  private def split(node: SourceNode): SourcePart = {
    val nextWithParts = traverse(node.id, node.next)
    SourcePart(splittednode.SourceNode(node.data, nextWithParts.next), nextWithParts.nextParts, nextWithParts.ends)
  }

  private def split(custom: CustomNode, next: SubsequentNode): CustomNodePart = {
    val nextWithParts = traverse(next)
    val node          = splittednode.OneOutputSubsequentNode(custom, nextWithParts.next)
    CustomNodePart(node, nextWithParts.nextParts, nextWithParts.ends)
  }

  private def split(custom: CustomNode): CustomNodePart = {
    val node = splittednode.EndingNode(custom)
    CustomNodePart(node, List.empty, List.empty)
  }

  private def split(sink: Sink): SinkPart = {
    val node = splittednode.EndingNode(sink)
    SinkPart(node)
  }

  private def traverse(nodeId: NodeId, nextNodeOpt: Option[SubsequentNode]): NextWithParts = {
    nextNodeOpt match {
      case Some(next) =>
        traverse(next)
      case None =>
        NextWithParts.end(nodeId)
    }
  }

  private def traverse(node: SubsequentNode): NextWithParts =
    node match {
      case FilterNode(data, nextTrue, nextFalse) =>
        (nextTrue.map(traverse), nextFalse.map(traverse)) match {
          case (Some(nextTrueT), Some(nextFalseT)) =>
            NextWithParts(
              NextNode(splittednode.FilterNode(data, nextTrueT.next, nextFalseT.next)),
              nextTrueT.nextParts ::: nextFalseT.nextParts,
              nextTrueT.ends ::: nextFalseT.ends
            )
          case (None, Some(nextFalseT)) =>
            NextWithParts(
              NextNode(splittednode.FilterNode(data, None, nextFalseT.next)),
              nextFalseT.nextParts,
              DeadEnd(data.id) :: nextFalseT.ends
            )
          case (Some(nextTrueT), None) =>
            NextWithParts(
              NextNode(splittednode.FilterNode(data, nextTrueT.next, None)),
              nextTrueT.nextParts,
              DeadEnd(data.id) :: nextTrueT.ends
            )
          case (None, None) =>
            NextWithParts(
              NextNode(splittednode.FilterNode(data, None, None)),
              List.empty,
              DeadEnd(data.id) :: Nil,
            )
        }
      case SwitchNode(data, nexts, defaultNext) =>
        val (nextsT, casesNextParts, casesEnds) = nexts.map { casee =>
          val nextWithParts = traverse(data.id, casee.node)
          (splittednode.Case(casee.expression, nextWithParts.next), nextWithParts.nextParts, nextWithParts.ends)
        }.unzip3
        defaultNext.map(traverse) match {
          case Some(defaultNextT) =>
            NextWithParts(
              NextNode(splittednode.SwitchNode(data, nextsT, defaultNextT.next)),
              defaultNextT.nextParts ::: casesNextParts.flatten,
              defaultNextT.ends ::: casesEnds.flatten
            )
          case None =>
            NextWithParts(
              NextNode(splittednode.SwitchNode(data, nextsT, None)),
              casesNextParts.flatten,
              DeadEnd(data.id) :: casesEnds.flatten
            )
        }
      case OneOutputSubsequentNode(custom: CustomNode, next) =>
        next match {
          case Some(next) =>
            val part = split(custom, next)
            NextWithParts(PartRef(part.id), List(part), List.empty)
          case None =>
            NextWithParts.end(custom.id)
        }
      case split: SplitNode =>
        val nextWithParts = split.nextParts.map(traverse)
        val node          = splittednode.SplitNode(split.data, nextWithParts.flatMap(_.next))
        NextWithParts(NextNode(node), nextWithParts.flatMap(_.nextParts), nextWithParts.flatMap(_.ends))
      case OneOutputSubsequentNode(other, next) =>
        traverse(other.id, next).map { nextT =>
          Some(NextNode(splittednode.OneOutputSubsequentNode(other, nextT)))
        }
      case EndingNode(sink: Sink) =>
        val part = split(sink)
        NextWithParts(PartRef(sink.id), List(part), List.empty)
      case EndingNode(endingCustomNode: CustomNode) =>
        val part = split(endingCustomNode)
        NextWithParts(PartRef(part.id), List(part), List.empty)
      case EndingNode(other) =>
        NextWithParts(NextNode(splittednode.EndingNode(other)), List.empty, List(NormalEnd(other.id)))
      case BranchEnd(branchEndData) =>
        NextWithParts(
          NextNode(splittednode.EndingNode(branchEndData)),
          List.empty,
          List(end.BranchEnd(branchEndData.definition))
        )
      case FragmentNode(id, _) =>
        throw new RuntimeException("Should not happen")

    }

  case class NextWithParts(next: Option[splittednode.Next], nextParts: List[SubsequentPart], ends: List[End]) {

    def map(f: Option[splittednode.Next] => Option[splittednode.Next]): NextWithParts = {
      copy(next = f(next))
    }

  }

  object NextWithParts {
    def apply(next: splittednode.Next, nextParts: List[SubsequentPart], ends: List[End]): NextWithParts =
      NextWithParts(Some(next), nextParts, ends)

    def end(nodeId: NodeId): NextWithParts =
      NextWithParts(None, List.empty, List(NormalEnd(nodeId)))
  }

}
