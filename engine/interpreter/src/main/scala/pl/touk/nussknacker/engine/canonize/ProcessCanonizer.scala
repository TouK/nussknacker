package pl.touk.nussknacker.engine.canonize

import cats.Applicative
import cats.data.{NonEmptyList, ValidatedNel}
import cats.instances.list._
import pl.touk.nussknacker.engine.api.context.ProcessUncanonizationError
import pl.touk.nussknacker.engine.canonicalgraph._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.graph._
import pl.touk.nussknacker.engine.graph.node.{BranchEnd, BranchEndData, BranchEndDefinition}

object ProcessCanonizer {
  import cats.syntax.apply._
  import cats.Traverse.ops._
  import MaybeArtificial.applicative

  def canonize(process: EspProcess): CanonicalProcess = {
    CanonicalProcess(
      process.metaData,
      process.exceptionHandlerRef,
      NodeCanonizer.canonize(process.roots.head),
      Some(process.roots.tail.map(NodeCanonizer.canonize))
    )
  }

  def uncanonize(canonicalProcess: CanonicalProcess): ValidatedNel[ProcessUncanonizationError, EspProcess] =
    uncanonizeArtificial(canonicalProcess).toValidNel

  def uncanonizeArtificial(canonicalProcess: CanonicalProcess): MaybeArtificial[EspProcess] = {

    val branches: MaybeArtificial[NonEmptyList[pl.touk.nussknacker.engine.graph.node.SourceNode]]
      = canonicalProcess.allStartNodes.map(uncanonizeSource).sequence

    branches.map(bList => EspProcess(canonicalProcess.metaData, canonicalProcess.exceptionHandlerRef, bList))
  }

  private def uncanonizeSource(canonicalNode: List[canonicalnode.CanonicalNode]): MaybeArtificial[node.SourceNode] =
    canonicalNode match {
      case (a@canonicalnode.FlatNode(data: node.StartingNodeData)) :: tail =>
        uncanonize(a, tail).map(node.SourceNode(data, _))

      case other :: _ =>
        MaybeArtificial.artificialSource(InvalidRootNode(other.id))

      case _ =>
        MaybeArtificial.artificialSource(EmptyProcess)
    }

  private def uncanonize(previous: canonicalnode.CanonicalNode,
                         canonicalNode: List[canonicalnode.CanonicalNode]): MaybeArtificial[node.SubsequentNode] =
    canonicalNode match {
      case canonicalnode.FlatNode(data: node.BranchEndData) :: Nil =>
        new MaybeArtificial(node.BranchEnd(data), Nil)
        
      case canonicalnode.FlatNode(data: node.EndingNodeData) :: Nil =>
        new MaybeArtificial(node.EndingNode(data), Nil)

      case (a@canonicalnode.FlatNode(data: node.OneOutputSubsequentNodeData)) :: tail =>
        uncanonize(a, tail).map(node.OneOutputSubsequentNode(data, _))

      case (a@canonicalnode.FilterNode(data, nextFalse)) :: tail if nextFalse.isEmpty =>
        uncanonize(a, tail).map(node.FilterNode(data, _, None))

      case (a@canonicalnode.FilterNode(data, nextFalse)) :: tail =>
        (uncanonize(a, tail), uncanonize(a, nextFalse)).mapN { (nextTrue, nextFalseV) =>
          node.FilterNode(data, nextTrue, Some(nextFalseV))
        }

      case (a@canonicalnode.SwitchNode(data, Nil, defaultNext)) :: Nil =>
        MaybeArtificial.artificialSink(InvalidTailOfBranch(data.id))

      case (a@canonicalnode.SwitchNode(data, nexts, defaultNext)) :: Nil if defaultNext.isEmpty =>
        nexts.map { casee =>
          uncanonize(a, casee.nodes).map(node.Case(casee.expression, _))
        }.sequence[MaybeArtificial, node.Case].map(node.SwitchNode(data, _, None))

      case (a@canonicalnode.SwitchNode(data, nexts, defaultNext)) :: Nil =>
        val unFlattenNexts = nexts.map { casee =>
          uncanonize(a, casee.nodes).map(node.Case(casee.expression, _))
        }.sequence[MaybeArtificial, node.Case]

        (unFlattenNexts, uncanonize(a, defaultNext)).mapN { (nextsV, defaultNextV) =>
          node.SwitchNode(data, nextsV, Some(defaultNextV))
        }

      case (a@canonicalnode.SplitNode(bare, Nil)) :: Nil =>
        MaybeArtificial.artificialSink(InvalidTailOfBranch(bare.id))

      case (a@canonicalnode.SplitNode(bare, nexts)) :: Nil =>
        nexts.map(uncanonize(a, _)).sequence[MaybeArtificial, node.SubsequentNode].map { uncanonized =>
          node.SplitNode(bare, uncanonized)
        }

      case invalidHead :: _ =>
        MaybeArtificial.artificialSink(InvalidTailOfBranch(invalidHead.id))

      case Nil =>
        MaybeArtificial.artificialSink(InvalidTailOfBranch(previous.id))
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
      case BranchEnd(e:BranchEndData) =>
        canonicalnode.FlatNode(e) :: Nil
    }

}