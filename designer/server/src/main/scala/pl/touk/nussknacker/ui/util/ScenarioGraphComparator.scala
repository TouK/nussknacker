package pl.touk.nussknacker.ui.util

import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.graph.node._

object ScenarioGraphComparator {

  def compare(currentGraph: ScenarioGraph, otherGraph: ScenarioGraph): Map[String, Difference] =
    PreparedCurrentGraph(currentGraph).compareWith(otherGraph)

  /** The current graph's lookup maps, built once so it can be compared against many others. */
  final class PreparedCurrentGraph private (
      nodes: Map[NodeId, NodeData],
      nodeNames: Map[NodeId, String],
      edges: Map[(NodeId, NodeId), Edge],
      stickyNotes: Map[String, StickyNote],
      properties: ProcessProperties
  ) {

    def compareWith(otherGraph: ScenarioGraph): Map[String, Difference] = {
      val nodeNameById: Map[NodeId, String] = nodeNames ++ otherGraph.nodes.map(node => node.id -> node.name.value)

      val nodeDiffs = getDifferences(
        nodes,
        otherGraph.nodes.map(node => node.id -> node).toMap
      )(
        notPresentInOther = current => NodeNotPresentInOther(current.name.value, current),
        notPresentInCurrent = other => NodeNotPresentInCurrent(other.name.value, other),
        different = (current, other) => NodeDifferent(current.name.value, current, other)
      )

      val edgeDiffs = getDifferences(
        edges,
        otherGraph.edges.map(edge => (edge.from, edge.to) -> edge).toMap
      )(
        notPresentInOther =
          current => EdgeNotPresentInOther(nodeNameById(current.from), nodeNameById(current.to), current),
        notPresentInCurrent = other => EdgeNotPresentInCurrent(nodeNameById(other.from), nodeNameById(other.to), other),
        different =
          (current, other) => EdgeDifferent(nodeNameById(current.from), nodeNameById(current.to), current, other)
      )

      val stickyNoteDiffs = getDifferences(
        stickyNotes,
        otherGraph.stickyNotes.map(node => node.id -> node).toMap
      )(
        notPresentInOther = current => StickyNotePresentInOther(current.id, current),
        notPresentInCurrent = other => StickyNotePresentInCurrent(other.id, other),
        different = (current, other) => StickyNoteDifferent(current.id, current, other)
      )

      val propertiesDiffs = if (properties != otherGraph.properties) {
        PropertiesDifferent(properties, otherGraph.properties) :: Nil
      } else {
        Nil
      }

      nodeDiffs ++ edgeDiffs ++ stickyNoteDiffs ++ propertiesDiffs.map(property => property.id -> property).toMap
    }

  }

  object PreparedCurrentGraph {

    def apply(currentGraph: ScenarioGraph): PreparedCurrentGraph = new PreparedCurrentGraph(
      nodes = currentGraph.nodes.map(node => node.id -> node).toMap,
      nodeNames = currentGraph.nodes.map(node => node.id -> node.name.value).toMap,
      edges = currentGraph.edges.map(edge => (edge.from, edge.to) -> edge).toMap,
      stickyNotes = currentGraph.stickyNotes.map(node => node.id -> node).toMap,
      properties = currentGraph.properties
    )

  }

  private def getDifferences[K, V](currents: Map[K, V], others: Map[K, V])(
      notPresentInOther: V => Difference,
      notPresentInCurrent: V => Difference,
      different: (V, V) => Difference
  ): Map[String, Difference] = {
    (currents.keys ++ others.keys).toSet
      .map((id: K) => (currents.get(id), others.get(id)))
      .collect {
        case (Some(current), None)                            => notPresentInOther(current)
        case (None, Some(other))                              => notPresentInCurrent(other)
        case (Some(current), Some(other)) if current != other => different(current, other)
      }
      .map(difference => difference.id -> difference)
      .toMap
  }

  private def meaningfulDiffs(diff: Map[String, Difference]): Map[String, Difference] =
    diff.filter {
      case (_, NodeDifferent(_, current, other))       => !isLayoutOnlyNodeDiff(current, other)
      case (_, StickyNoteDifferent(_, current, other)) => !isLayoutOnlyStickyNoteDiff(current, other)
      case _                                           => true
    }

  /**
   * Up to `limit` lines describing the meaningful changes, plus how many there are in total. Sorted so
   * that the same pair of graphs always yields the same order. Only the lines that are returned are built
   * - a version that rewrote a large scenario has as many changes as it has nodes, and describing all of
   * them to then discard all but `limit` is the expensive part.
   */
  def describeMeaningfulDiffs(diff: Map[String, Difference], limit: Int): (List[String], Int) = {
    val meaningful = meaningfulDiffs(diff).values.toList
    (meaningful.sortBy(sortKey).take(limit).map(describe), meaningful.size)
  }

  private def sortKey(difference: Difference): (Int, String, String) = difference match {
    case PropertiesDifferent(_, _)            => (0, "", "")
    case NodeNotPresentInOther(id, _)         => (1, id, "")
    case NodeNotPresentInCurrent(id, _)       => (1, id, "")
    case NodeDifferent(id, _, _)              => (1, id, "")
    case EdgeNotPresentInOther(from, to, _)   => (2, from, to)
    case EdgeNotPresentInCurrent(from, to, _) => (2, from, to)
    case EdgeDifferent(from, to, _, _)        => (2, from, to)
    case StickyNotePresentInOther(id, _)      => (3, id, "")
    case StickyNotePresentInCurrent(id, _)    => (3, id, "")
    case StickyNoteDifferent(id, _, _)        => (3, id, "")
  }

  private def describe(difference: Difference): String = difference match {
    case PropertiesDifferent(_, _)            => "Properties modified"
    case NodeNotPresentInOther(id, _)         => s"Node '$id' added"
    case NodeNotPresentInCurrent(id, _)       => s"Node '$id' removed"
    case NodeDifferent(id, _, _)              => s"Node '$id' modified"
    case EdgeNotPresentInOther(from, to, _)   => s"Edge '$from' → '$to' added"
    case EdgeNotPresentInCurrent(from, to, _) => s"Edge '$from' → '$to' removed"
    case EdgeDifferent(from, to, _, _)        => s"Edge '$from' → '$to' modified"
    case StickyNotePresentInOther(id, _)      => s"Note '$id' added"
    case StickyNotePresentInCurrent(id, _)    => s"Note '$id' removed"
    case StickyNoteDifferent(id, _, _)        => s"Note '$id' modified"
  }

  private def isLayoutOnlyNodeDiff(current: NodeData, other: NodeData): Boolean =
    withoutLayoutData(current) == withoutLayoutData(other)

  private def isLayoutOnlyStickyNoteDiff(current: StickyNote, other: StickyNote): Boolean =
    current.copy(additionalFields = withoutLayoutData(current.additionalFields)) ==
      other.copy(additionalFields = withoutLayoutData(other.additionalFields))

  // stored scenarios spell "no extra fields" in several ways - absent, present but empty, or an empty
  // description - and none of them is a change the user made
  private def withoutLayoutData(
      additionalFields: Option[UserDefinedAdditionalNodeFields]
  ): Option[UserDefinedAdditionalNodeFields] =
    additionalFields
      .map(fields => fields.copy(description = fields.description.filter(_.nonEmpty), layoutData = None))
      .filterNot(_ == UserDefinedAdditionalNodeFields(None, None))

  private def withoutLayoutData(node: NodeData): NodeData = node match {
    case n: Source                   => n.copy(additionalFields = withoutLayoutData(n.additionalFields))
    case n: Join                     => n.copy(additionalFields = withoutLayoutData(n.additionalFields))
    case n: Filter                   => n.copy(additionalFields = withoutLayoutData(n.additionalFields))
    case n: Switch                   => n.copy(additionalFields = withoutLayoutData(n.additionalFields))
    case n: VariableBuilder          => n.copy(additionalFields = withoutLayoutData(n.additionalFields))
    case n: Variable                 => n.copy(additionalFields = withoutLayoutData(n.additionalFields))
    case n: Split                    => n.copy(additionalFields = withoutLayoutData(n.additionalFields))
    case n: Enricher                 => n.copy(additionalFields = withoutLayoutData(n.additionalFields))
    case n: CustomNode               => n.copy(additionalFields = withoutLayoutData(n.additionalFields))
    case n: Processor                => n.copy(additionalFields = withoutLayoutData(n.additionalFields))
    case n: Sink                     => n.copy(additionalFields = withoutLayoutData(n.additionalFields))
    case n: FragmentInput            => n.copy(additionalFields = withoutLayoutData(n.additionalFields))
    case n: FragmentUsageOutput      => n.copy(additionalFields = withoutLayoutData(n.additionalFields))
    case n: FragmentInputDefinition  => n.copy(additionalFields = withoutLayoutData(n.additionalFields))
    case n: FragmentOutputDefinition => n.copy(additionalFields = withoutLayoutData(n.additionalFields))
    case n: BranchEndData            => n
  }

  @ConfiguredJsonCodec sealed trait Difference {
    def id: String
  }

  sealed trait NodeDifference extends Difference {
    def nodeId: String

    override def id: String = s"Node '$nodeId'"
  }

  final case class StickyNoteDifferent(nodeId: String, currentStickyNote: StickyNote, otherStickyNote: StickyNote)
      extends NodeDifference

  final case class StickyNotePresentInOther(nodeId: String, currentStickyNote: StickyNote) extends NodeDifference

  final case class StickyNotePresentInCurrent(nodeId: String, otherStickyNote: StickyNote) extends NodeDifference

  final case class NodeDifferent(nodeId: String, currentNode: NodeData, otherNode: NodeData) extends NodeDifference

  final case class NodeNotPresentInOther(nodeId: String, currentNode: NodeData) extends NodeDifference

  final case class NodeNotPresentInCurrent(nodeId: String, otherNode: NodeData) extends NodeDifference

  sealed trait EdgeDifference extends Difference {
    def fromId: String
    def toId: String

    override def id: String = s"Edge from '$fromId' to '$toId'"
  }

  final case class EdgeDifferent(fromId: String, toId: String, currentEdge: Edge, otherEdge: Edge)
      extends EdgeDifference

  final case class EdgeNotPresentInOther(fromId: String, toId: String, currentEdge: Edge) extends EdgeDifference

  final case class EdgeNotPresentInCurrent(fromId: String, toId: String, otherEdge: Edge) extends EdgeDifference

  final case class PropertiesDifferent(currentProperties: ProcessProperties, otherProperties: ProcessProperties)
      extends Difference {
    override def id: String = "Properties"
  }

}
