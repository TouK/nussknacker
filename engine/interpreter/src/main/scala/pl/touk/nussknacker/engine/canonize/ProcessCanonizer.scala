package pl.touk.nussknacker.engine.canonize

import cats.data.Validated._
import cats.data.ValidatedNel
import cats.instances.list._
import pl.touk.nussknacker.engine.canonicalgraph._
import pl.touk.nussknacker.engine.compile.ProcessCompilationError._
import pl.touk.nussknacker.engine.compile.{ProcessUncanonizationError, ValidatedSyntax}
import pl.touk.nussknacker.engine.graph._

object ProcessCanonizer {

  private val syntax = ValidatedSyntax[ProcessUncanonizationError]
  import syntax._

  def canonize(process: EspProcess): CanonicalProcess = {
    CanonicalProcess(
      process.metaData,
      process.exceptionHandlerRef,
      NodeCanonizer.canonize(process.root)
    )
  }

  def uncanonize(canonicalProcess: CanonicalProcess): ValidatedNel[ProcessUncanonizationError, EspProcess] =
    uncanonizeSource(canonicalProcess.nodes).map(
      EspProcess(canonicalProcess.metaData, canonicalProcess.exceptionHandlerRef, _))

  private def uncanonizeSource(canonicalNode: List[canonicalnode.CanonicalNode]): ValidatedNel[ProcessUncanonizationError, node.SourceNode] =
    canonicalNode match {
      case (a@canonicalnode.FlatNode(data: node.StartingNodeData)) :: tail =>
        uncanonize(a, tail).map(node.SourceNode(data, _))
      case other :: tail =>
        invalid(InvalidRootNode(other.id)).toValidatedNel
      case invalidTail =>
        invalid(EmptyProcess).toValidatedNel
    }

  private def uncanonize(previous: canonicalnode.CanonicalNode,
                         canonicalNode: List[canonicalnode.CanonicalNode]): ValidatedNel[ProcessUncanonizationError, node.SubsequentNode] =
    canonicalNode match {
      case canonicalnode.FlatNode(data: node.EndingNodeData) :: Nil =>
        valid(node.EndingNode(data))
      case (a@canonicalnode.FlatNode(data: node.OneOutputSubsequentNodeData)) :: tail =>
        uncanonize(a, tail).map(node.OneOutputSubsequentNode(data, _))
      case (a@canonicalnode.FilterNode(data, nextFalse)) :: tail if nextFalse.isEmpty =>
        uncanonize(a, tail).map(node.FilterNode(data, _, None))
      case (a@canonicalnode.FilterNode(data, nextFalse)) :: tail =>
        A.map2(uncanonize(a, tail), uncanonize(a, nextFalse)) { (nextTrue, nextFalseV) =>
          node.FilterNode(data, nextTrue, Some(nextFalseV))
        }
      case (a@canonicalnode.SwitchNode(data, Nil, defaultNext)) :: Nil =>
        invalid(InvalidTailOfBranch(data.id)).toValidatedNel
      case (a@canonicalnode.SwitchNode(data, nexts, defaultNext)) :: Nil if defaultNext.isEmpty =>
        nexts.map { casee =>
          uncanonize(a, casee.nodes).map(node.Case(casee.expression, _))
        }.sequence.map(node.SwitchNode(data, _, None))
      case (a@canonicalnode.SwitchNode(data, nexts, defaultNext)) :: Nil =>
        val unFlattenNexts = nexts.map { casee =>
          uncanonize(a, casee.nodes).map(node.Case(casee.expression, _))
        }.sequence
        A.map2(unFlattenNexts, uncanonize(a, defaultNext)) { (nextsV, defaultNextV) =>
          node.SwitchNode(data, nextsV, Some(defaultNextV))
        }
      case (a@canonicalnode.SplitNode(bare, Nil)) :: Nil=>
        invalid(InvalidTailOfBranch(bare.id)).toValidatedNel
      case (a@canonicalnode.SplitNode(bare, nexts)) :: Nil=>
        nexts.map(uncanonize(a, _)).sequence.map { uncanonized =>
          node.SplitNode(bare, uncanonized)
        }
      case invalidHead :: _ =>
        invalid(InvalidTailOfBranch(invalidHead.id)).toValidatedNel
      case Nil => invalid(InvalidTailOfBranch(previous.id)).toValidatedNel
    }

}

object NodeCanonizer {

  def canonize(n: node.Node): List[canonicalnode.CanonicalNode] =
    n match {
      case oneOut: node.OneOutputNode =>
        canonicalnode.FlatNode(oneOut.data) :: canonize(oneOut.next)
      case node.FilterNode(data, nextTrue, nextFalse) =>
        canonicalnode.FilterNode(data, nextFalse.toList.flatMap(canonize)) :: canonize(nextTrue)
      case node.SwitchNode(data, nexts, defaultNext) =>
        canonicalnode.SwitchNode(
          data = data,
          nexts = nexts.map { next =>
            canonicalnode.Case(next.expression, canonize(next.node))
          },
          defaultNext = defaultNext.toList.flatMap(canonize)
        ) :: Nil
      case ending: node.EndingNode =>
        canonicalnode.FlatNode(ending.data) :: Nil
      case node.SplitNode(bare, nexts) =>
        canonicalnode.SplitNode(bare, nexts.map(canonize)) :: Nil
      case node.SubprocessNode(input, nexts) =>
        canonicalnode.Subprocess(input, nexts.mapValues(canonize)) :: Nil
    }

}