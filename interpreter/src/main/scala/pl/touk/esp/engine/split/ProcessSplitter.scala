package pl.touk.esp.engine.split

import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.splittedgraph._
import pl.touk.esp.engine.splittedgraph.end.{DeadEnd, End, NormalEnd}
import pl.touk.esp.engine.splittedgraph.part._
import pl.touk.esp.engine.splittedgraph.splittednode.{NextNode, PartRef}

object ProcessSplitter {

  def split(process: EspProcess): SplittedProcess = {
    SplittedProcess(process.metaData, process.exceptionHandlerRef, split(process.root))
  }

  private def split(node: SourceNode): SourcePart = {
    val nextWithParts = traverse(node.next)
    SourcePart(splittednode.SourceNode(node.data, nextWithParts.next), nextWithParts.nextParts, nextWithParts.ends)
  }

  private def split(aggregate: Aggregate, next: SubsequentNode): AggregatePart = {
    val nextWithParts = traverse(next)
    val node = splittednode.OneOutputSubsequentNode(aggregate, nextWithParts.next)
    AggregatePart(node, nextWithParts.nextParts, nextWithParts.ends)
  }

  private def split(custom: CustomNode, next: SubsequentNode) : CustomNodePart = {
    val nextWithParts = traverse(next)
    val node = splittednode.OneOutputSubsequentNode(custom, nextWithParts.next)
    CustomNodePart(node, nextWithParts.nextParts, nextWithParts.ends)
  }

  private def split(sink: Sink): SinkPart = {
    val node = splittednode.EndingNode(sink)
    SinkPart(node)
  }

  private def traverse(node: SubsequentNode): NextWithParts =
    node match {
      case FilterNode(data, nextTrue, nextFalse) =>
        val nextTrueT = traverse(nextTrue)
        nextFalse.map(traverse) match {
          case Some(nextFalseT) =>
            NextWithParts(
              NextNode(splittednode.FilterNode(data, nextTrueT.next, Some(nextFalseT.next))),
              nextTrueT.nextParts ::: nextFalseT.nextParts,
              nextTrueT.ends ::: nextFalseT.ends
            )
          case None =>
            NextWithParts(
              NextNode(splittednode.FilterNode(data, nextTrueT.next, None)),
              nextTrueT.nextParts,
              DeadEnd(data.id) :: nextTrueT.ends
            )
        }
      case SwitchNode(data, nexts, defaultNext) =>
        val (nextsT, casesNextParts, casesEnds) = nexts.map { casee =>
          val nextWithParts = traverse(casee.node)
          (splittednode.Case(casee.expression, nextWithParts.next), nextWithParts.nextParts, nextWithParts.ends)
        }.unzip3
        defaultNext.map(traverse) match {
          case Some(defaultNextT) =>
            NextWithParts(
              NextNode(splittednode.SwitchNode(data, nextsT, Some(defaultNextT.next))),
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
        val part = split(custom, next)
        NextWithParts(PartRef(part.id), List(part), List.empty)
      case OneOutputSubsequentNode(aggregate: Aggregate, next) =>
        val part = split(aggregate, next)
        NextWithParts(PartRef(part.id), List(part), List.empty)
      case OneOutputSubsequentNode(other, next) =>
        traverse(next).map { nextT =>
          NextNode(splittednode.OneOutputSubsequentNode(other, nextT))
        }
      case EndingNode(sink: Sink) =>
        val part = split(sink)
        NextWithParts(PartRef(sink.id), List(part), List.empty)
      case EndingNode(other) =>
        NextWithParts(NextNode(splittednode.EndingNode(other)), List.empty, List(NormalEnd(other.id)))
    }

  case class NextWithParts(next: splittednode.Next, nextParts: List[SubsequentPart], ends: List[End]) {

    def map(f: splittednode.Next => splittednode.Next): NextWithParts = {
      copy(next = f(next))
    }

  }

}