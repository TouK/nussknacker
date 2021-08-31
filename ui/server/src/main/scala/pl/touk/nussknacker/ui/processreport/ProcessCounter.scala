package pl.touk.nussknacker.ui.processreport

import cats.data.NonEmptyList
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.ProcessAdditionalFields
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode._
import pl.touk.nussknacker.engine.graph.node.{BranchEndData, SubprocessInputDefinition}
import pl.touk.nussknacker.ui.process.subprocess.SubprocessRepository
import shapeless.syntax.typeable._

class ProcessCounter(subprocessRepository: SubprocessRepository) {


  def computeCounts(canonicalProcess: CanonicalProcess, counts: String => Option[RawCount]) : Map[String, NodeCount] = {

    def computeCounts(prefixes: List[String])(nodes: NonEmptyList[Iterable[CanonicalNode]]) : Map[String, NodeCount] = {
      nodes.map(startNode => computeCountsForSingle(prefixes)(startNode)).toList.reduceOption(_ ++ _).getOrElse(Map.empty)
    }

    def computeCountsForSingle(prefixes: List[String])(nodes: Iterable[CanonicalNode]) : Map[String, NodeCount] = {

      val computeCountsSamePrefixes = computeCountsForSingle(prefixes) _

      def nodeCount(id: String, subprocessCounts: Map[String, NodeCount] = Map()) : NodeCount =
        nodeCountOption(Some(id), subprocessCounts)

      def nodeCountOption(id: Option[String], subprocessCounts: Map[String, NodeCount] = Map()) : NodeCount = {
        val countId = (prefixes ++ id).mkString("-")
        val count = counts(countId).getOrElse(RawCount(0L, 0L))
        NodeCount(count.all, count.errors, subprocessCounts)
      }

      nodes.flatMap {
        //TODO: this is a bit of a hack. Metric for subprocess input is counted in node with subprocess occurrence id...
        case FlatNode(SubprocessInputDefinition(id, _, _)) => Map(id -> nodeCountOption(None))
        //BranchEndData is kind of artifical entity
        case FlatNode(BranchEndData(_)) => Map.empty[String, NodeCount]
        case FlatNode(node) => Map(node.id -> nodeCount(node.id))
        case FilterNode(node, nextFalse) => computeCountsSamePrefixes(nextFalse) + (node.id -> nodeCount(node.id))
        case SwitchNode(node, nexts, defaultNext) =>
          computeCountsSamePrefixes(nexts.flatMap(_.nodes)) ++ computeCountsSamePrefixes(defaultNext) + (node.id -> nodeCount(node.id))
        case SplitNode(node, nexts) => computeCountsSamePrefixes(nexts.flatten) + (node.id -> nodeCount(node.id))
        case Subprocess(node, outputs) =>
          //TODO: validate that process exists
          val subprocess = getSubprocess(canonicalProcess.metaData.subprocessVersions, node.ref.id).get
          computeCountsSamePrefixes(outputs.values.flatten) + (node.id -> nodeCount(node.id,
            computeCounts(prefixes :+ node.id)(subprocess.allStartNodes)))
      }.toMap

    }
    val valuesWithoutGroups = computeCounts(List())(canonicalProcess.allStartNodes)
    valuesWithoutGroups
  }

  private def getSubprocess(subprocessVersions: Map[String, Long], subprocessId: String): Option[CanonicalProcess] = {
    val subprocess = subprocessVersions.get(subprocessId) match {
      case Some(version) => subprocessRepository.get(subprocessId, version)
      case None => subprocessRepository.get(subprocessId)
    }
    subprocess.map(_.canonical)
  }

}


case class RawCount(all: Long, errors: Long)

@JsonCodec case class NodeCount(all: Long, errors: Long, subprocessCounts: Map[String, NodeCount] = Map())