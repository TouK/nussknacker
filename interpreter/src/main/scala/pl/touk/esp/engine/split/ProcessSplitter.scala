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

  private def split(node: Source): SourcePart = {
    val nextWithParts = traverse(node.next)
    SourcePart(node.id, node.ref, splittednode.Source(node.id, nextWithParts.next), nextWithParts.nextParts, nextWithParts.ends)
  }

  private def split(node: Aggregate): AggregatePart = {
    val nextWithParts = traverse(node.next)
    val aggregate = splittednode.Aggregate(node.id, node.keyExpression, node.triggerExpression, nextWithParts.next)
    AggregatePart(node.id, node.durationInMillis, node.stepInMillis, node.aggregatedVar,
      node.foldingFunRef, aggregate, nextWithParts.nextParts, nextWithParts.ends)
  }

  private def split(node: Sink): SinkPart = {
    SinkPart(node.id, node.ref, splittednode.Sink(node.id, node.endResult))
  }

  private def traverse(node: Node): NextWithParts =
    node match {
      case source: Source =>
        throw new IllegalArgumentException("Source shouldn't be traversed")
      case VariableBuilder(id, varName, fields, next) =>
        traverse(next).map { nextT =>
          NextNode(splittednode.VariableBuilder(id, varName, fields, nextT))
        }
      case Processor(id, service, next) =>
        traverse(next).map { nextT =>
          NextNode(splittednode.Processor(id, service, nextT))
        }
      case Enricher(id, service, output, next) =>
        traverse(next).map { nextT =>
          NextNode(splittednode.Enricher(id, service, output, nextT))
        }
      case Filter(id, expression, nextTrue, nextFalse) =>
        val nextTrueT = traverse(nextTrue)
        nextFalse.map(traverse) match {
          case Some(nextFalseT) =>
            NextWithParts(
              NextNode(splittednode.Filter(id, expression, nextTrueT.next, Some(nextFalseT.next))),
              nextTrueT.nextParts ::: nextFalseT.nextParts,
              nextTrueT.ends ::: nextFalseT.ends
            )
          case None =>
            NextWithParts(
              NextNode(splittednode.Filter(id, expression, nextTrueT.next, None)),
              nextTrueT.nextParts,
              DeadEnd(id) :: nextTrueT.ends
            )
        }
      case Switch(id, expression, exprVal, nexts, defaultNext) =>
        val (nextsT, casesNextParts, casesEnds) = nexts.map { casee =>
          val nextWithParts = traverse(casee.node)
          (splittednode.Case(casee.expression, nextWithParts.next), nextWithParts.nextParts, nextWithParts.ends)
        }.unzip3
        defaultNext.map(traverse) match {
          case Some(defaultNextT) =>
            NextWithParts(
              NextNode(splittednode.Switch(id, expression, exprVal, nextsT, Some(defaultNextT.next))),
              defaultNextT.nextParts ::: casesNextParts.flatten,
              defaultNextT.ends ::: casesEnds.flatten
            )
          case None =>
            NextWithParts(
              NextNode(splittednode.Switch(id, expression, exprVal, nextsT, None)),
              casesNextParts.flatten,
              DeadEnd(id) :: casesEnds.flatten
            )
        }
      case sink: Sink =>
        val part = split(sink)
        NextWithParts(PartRef(part.id), List(part), List.empty)
      case end: EndingProcessor =>
        NextWithParts(NextNode(splittednode.EndingProcessor(end.id, end.service)), List.empty, List(NormalEnd(end.id)))
      case aggregate: Aggregate =>
        val part = split(aggregate)
        NextWithParts(PartRef(part.id), List(part), List.empty)
    }

  case class NextWithParts(next: splittednode.Next, nextParts: List[SubsequentPart], ends: List[End]) {

    def map(f: splittednode.Next => splittednode.Next): NextWithParts = {
      copy(next = f(next))
    }

  }

}