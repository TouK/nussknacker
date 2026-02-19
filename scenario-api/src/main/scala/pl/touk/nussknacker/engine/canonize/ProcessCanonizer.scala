package pl.touk.nussknacker.engine.canonize

import cats.data.{NonEmptyList, ValidatedNel}
import cats.instances.list._
import cats.syntax.traverse._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.canonicalgraph._
import pl.touk.nussknacker.engine.graph._
import pl.touk.nussknacker.engine.graph.node.{BranchEnd, BranchEndData}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

object ProcessCanonizer {

  import cats.syntax.apply._

  import MaybeArtificial.applicative

  def canonize(process: EspProcess): CanonicalProcess = {
    CanonicalProcess(
      process.metaData,
      NodeCanonizer.canonize(process.roots.head),
      process.roots.tail.map(NodeCanonizer.canonize)
    )
  }

  def uncanonize(
      canonicalProcess: CanonicalProcess,
      missingSinkHandler: MissingSinkHandler,
  ): ValidatedNel[ProcessUncanonizationError, EspProcess] =
    uncanonizeArtificial(canonicalProcess, missingSinkHandler).toValidNel

  def uncanonizeArtificial(
      canonicalProcess: CanonicalProcess,
      missingSinkHandler: MissingSinkHandler,
  ): MaybeArtificial[EspProcess] = {

    val branches: MaybeArtificial[NonEmptyList[pl.touk.nussknacker.engine.graph.node.SourceNode]] =
      canonicalProcess.allStartNodes.map(uncanonizeSource(_)(missingSinkHandler)).sequence

    branches.map(bList => EspProcess(canonicalProcess.metaData, bList))
  }

  private def uncanonizeSource(
      canonicalNode: List[canonicalnode.CanonicalNode],
  )(implicit missingSinkHandler: MissingSinkHandler): MaybeArtificial[node.SourceNode] =
    canonicalNode match {
      case (a @ canonicalnode.FlatNode(data: node.StartingNodeData)) :: tail =>
        uncanonize(a, tail).map(node.SourceNode(data, _))
      case other :: _ =>
        MaybeArtificial.artificialSource(InvalidRootNode(other.id))

      case _ =>
        MaybeArtificial.artificialSource(EmptyProcess)
    }

  private def uncanonize(
      previous: canonicalnode.CanonicalNode,
      canonicalNode: List[canonicalnode.CanonicalNode],
  )(implicit missingSinkHandler: MissingSinkHandler): MaybeArtificial[Option[node.SubsequentNode]] =
    canonicalNode match {
      case canonicalnode.FlatNode(data: node.BranchEndData) :: Nil =>
        new MaybeArtificial(Some(node.BranchEnd(data)), Nil)

      case canonicalnode.FlatNode(data: node.EndingNodeData) :: Nil =>
        new MaybeArtificial(Some(node.EndingNode(data)), Nil)

      case (a @ canonicalnode.FlatNode(data: node.OneOutputSubsequentNodeData)) :: tail =>
        uncanonize(a, tail).map(node.OneOutputSubsequentNode(data, _)).map(Some(_): Option[node.SubsequentNode])
      case (a @ canonicalnode.FilterNode(data, nextFalse)) :: tail if nextFalse.isEmpty =>
        uncanonize(a, tail).map(nextTrue => Some(node.FilterNode(data, nextTrue, None)))

      case (a @ canonicalnode.FilterNode(data, nextFalse)) :: tail if tail.isEmpty =>
        uncanonize(a, nextFalse).map { nextFalseV => Some(node.FilterNode(data, None, nextFalseV)) }

      case (a @ canonicalnode.FilterNode(data, nextFalse)) :: tail =>
        (uncanonize(a, tail), uncanonize(a, nextFalse)).mapN { (nextTrue, nextFalseV) =>
          Some(node.FilterNode(data, nextTrue, nextFalseV))
        }

      case (a @ canonicalnode.SwitchNode(data, Nil, defaultNext)) :: Nil =>
        uncanonize(a, defaultNext).map(defaultNextV => Some(node.SwitchNode(data, Nil, defaultNextV)))

      case (a @ canonicalnode.SwitchNode(data, nexts, defaultNext)) :: Nil if defaultNext.isEmpty =>
        nexts
          .map(casee => uncanonize(a, casee.nodes).map(node.Case(casee.expression, _)))
          .sequence[MaybeArtificial, node.Case]
          .map(node.SwitchNode(data, _, None))
          .map(Some(_))

      case (a @ canonicalnode.SwitchNode(data, nexts, defaultNext)) :: Nil =>
        val unFlattenNexts = nexts
          .map(casee => uncanonize(a, casee.nodes).map(node.Case(casee.expression, _)))
          .sequence[MaybeArtificial, node.Case]

        (unFlattenNexts, uncanonize(a, defaultNext)).mapN { (nextsV, defaultNextV) =>
          Some(node.SwitchNode(data, nextsV, defaultNextV))
        }

      case canonicalnode.SplitNode(bare, Nil) :: Nil =>
        missingSinkHandler.handleMissingSink(bare.id, Some(node.SplitNode(bare, List.empty)))

      case (a @ canonicalnode.SplitNode(bare, nexts)) :: Nil =>
        nexts
          .map(uncanonize(a, _))
          .sequence[MaybeArtificial, Option[node.SubsequentNode]]
          .map { uncanonized =>
            Some(node.SplitNode(bare, uncanonized.flatten))
          }

      case invalidHead :: _ =>
        MaybeArtificial.missingSinkError(InvalidTailOfBranch(invalidHead.id))

      case Nil =>
        missingSinkHandler.handleMissingSink(previous.id, None)
    }

}

object NodeCanonizer {

  def canonize(n: node.Node): List[canonicalnode.CanonicalNode] =
    n match {
      case oneOut: node.OneOutputNode =>
        canonicalnode.FlatNode(oneOut.data) :: oneOut.next.map(canonize).getOrElse(Nil)
      case node.FilterNode(data, nextTrue, nextFalse) =>
        canonicalnode.FilterNode(data, nextFalse.toList.flatMap(canonize)) :: nextTrue.toList.flatMap(canonize)
      case node.SwitchNode(data, nexts, defaultNext) =>
        canonicalnode.SwitchNode(
          data = data,
          nexts = nexts.map { next =>
            canonicalnode.Case(next.expression, next.node.map(canonize).getOrElse(Nil))
          },
          defaultNext = defaultNext.toList.flatMap(canonize)
        ) :: Nil
      case ending: node.EndingNode =>
        canonicalnode.FlatNode(ending.data) :: Nil
      case node.SplitNode(bare, nexts) =>
        canonicalnode.SplitNode(bare, nexts.map(canonize)) :: Nil
      case node.FragmentNode(input, nexts) =>
        canonicalnode.Fragment(input, nexts.mapValuesNow(_.map(canonize).getOrElse(List.empty))) :: Nil
      case BranchEnd(e: BranchEndData) =>
        canonicalnode.FlatNode(e) :: Nil
    }

}
