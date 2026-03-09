package pl.touk.nussknacker.ui.processreport

import cats.data.NonEmptyList
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode._
import pl.touk.nussknacker.engine.graph.node.{BranchEndData, FragmentInputDefinition}
import pl.touk.nussknacker.ui.process.fragment.FragmentRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser

class ProcessCounter(fragmentRepository: FragmentRepository) {

  def computeCounts(canonicalProcess: CanonicalProcess, isFragment: Boolean, counts: NodeId => Option[RawCount])(
      implicit user: LoggedUser
  ): Map[NodeId, NodeCount] = {

    def computeCounts(prefixes: List[NodeId])(nodes: NonEmptyList[Iterable[CanonicalNode]]): Map[NodeId, NodeCount] = {
      nodes
        .map(startNode => computeCountsForSingle(prefixes)(startNode))
        .toList
        .reduceOption(_ ++ _)
        .getOrElse(Map.empty)
    }

    def computeCountsForSingle(prefixes: List[NodeId])(nodes: Iterable[CanonicalNode]): Map[NodeId, NodeCount] = {

      val computeCountsSamePrefixes = computeCountsForSingle(prefixes) _

      def nodeCount(nodeId: NodeId, fragmentCounts: Map[NodeId, NodeCount] = Map()): NodeCount =
        nodeCountOption(Some(nodeId), fragmentCounts)

      def nodeCountOption(nodeId: Option[NodeId], fragmentCounts: Map[NodeId, NodeCount] = Map()): NodeCount = {
        // TODO: we should distinguish between NodeId for nodes of graph and something like "node invocation path"
        val countId = NodeId((prefixes ++ nodeId).mkString("-"))
        val count   = counts(countId).getOrElse(RawCount(0L, 0L))
        NodeCount(count.all, count.errors, fragmentCounts)
      }

      nodes.flatMap {
        // TODO: this is a bit of a hack. Metric for fragment input is counted in node with fragment occurrence id...
        // We want to count it though while testing fragments
        case FlatNode(FragmentInputDefinition(id, _, _, _)) if !isFragment =>
          Map(id -> nodeCountOption(None))
        // BranchEndData is kind of artificial entity
        case FlatNode(BranchEndData(_)) => Map.empty[NodeId, NodeCount]
        case FlatNode(node)             => Map(node.id -> nodeCount(node.id))
        case FilterNode(node, nextFalse) =>
          computeCountsSamePrefixes(nextFalse) + (node.id -> nodeCount(node.id))
        case SwitchNode(node, nexts, defaultNext) =>
          computeCountsSamePrefixes(nexts.flatMap(_.nodes)) ++ computeCountsSamePrefixes(
            defaultNext
          ) + (node.id -> nodeCount(node.id))
        case SplitNode(node, nexts) =>
          computeCountsSamePrefixes(nexts.flatten) + (node.id -> nodeCount(node.id))
        case Fragment(node, outputs) =>
          // TODO: validate that process exists
          val fragment = fragmentRepository.fetchLatestFragmentSync(ProcessName(node.ref.id)).get
          computeCountsSamePrefixes(outputs.values.flatten) + (node.id -> nodeCount(
            node.id,
            computeCounts(prefixes :+ node.id)(fragment.allStartNodes)
          ))
      }.toMap

    }

    computeCounts(List())(canonicalProcess.allStartNodes)
  }

}

final case class RawCount(all: Long, errors: Long)

@JsonCodec final case class NodeCount(all: Long, errors: Long, fragmentCounts: Map[NodeId, NodeCount] = Map())
