package pl.touk.nussknacker.ui.util

import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.graph.node._

object ScenarioGraphComparator {

  def compare(currentGraph: ScenarioGraph, otherGraph: ScenarioGraph): Map[String, Difference] = {
    val nodes = getDifferences(
      currentGraph.nodes.map(node => node.id -> node).toMap,
      otherGraph.nodes.map(node => node.id -> node).toMap
    )(
      notPresentInOther = current => NodeNotPresentInOther(current.id, current),
      notPresentInCurrent = other => NodeNotPresentInCurrent(other.id, other),
      different = (current, other) => NodeDifferent(current.id, current, other)
    )

    val edges = getDifferences(
      currentGraph.edges.map(edge => (edge.from, edge.to) -> edge).toMap,
      otherGraph.edges.map(edge => (edge.from, edge.to) -> edge).toMap
    )(
      notPresentInOther = current => EdgeNotPresentInOther(current.from, current.to, current),
      notPresentInCurrent = other => EdgeNotPresentInCurrent(other.from, other.to, other),
      different = (current, other) => EdgeDifferent(current.from, current.to, current, other)
    )

    val stickyNotes = getDifferences(
      currentGraph.stickyNotes.map(node => node.id -> node).toMap,
      otherGraph.stickyNotes.map(node => node.id -> node).toMap
    )(
      notPresentInOther = current => StickyNotePresentInOther(current.id, current),
      notPresentInCurrent = other => StickyNotePresentInCurrent(other.id, other),
      different = (current, other) => StickyNoteDifferent(current.id, current, other)
    )

    val properties = if (currentGraph.properties != otherGraph.properties) {
      PropertiesDifferent(currentGraph.properties, otherGraph.properties) :: Nil
    } else {
      Nil
    }

    nodes ++ edges ++ stickyNotes ++ properties.map(property => property.id -> property).toMap
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

  def meaningfulDiffs(diff: Map[String, Difference]): Map[String, Difference] =
    diff.filter {
      case (_, NodeDifferent(_, current, other))       => !isLayoutOnlyNodeDiff(current, other)
      case (_, StickyNoteDifferent(_, current, other)) => !isLayoutOnlyStickyNoteDiff(current, other)
      case _                                           => true
    }

  def hasMeaningfulDifferences(diff: Map[String, Difference]): Boolean = meaningfulDiffs(diff).nonEmpty

  def describeMeaningfulDiffs(diff: Map[String, Difference]): List[String] =
    meaningfulDiffs(diff).values.map {
      case NodeDifferent(id, _, _)              => s"Node '$id' modified"
      case NodeNotPresentInOther(id, _)         => s"Node '$id' added"
      case NodeNotPresentInCurrent(id, _)       => s"Node '$id' removed"
      case EdgeDifferent(from, to, _, _)        => s"Edge '$from' → '$to' modified"
      case EdgeNotPresentInOther(from, to, _)   => s"Edge '$from' → '$to' added"
      case EdgeNotPresentInCurrent(from, to, _) => s"Edge '$from' → '$to' removed"
      case StickyNoteDifferent(id, _, _)        => s"Note '$id' modified"
      case StickyNotePresentInOther(id, _)      => s"Note '$id' added"
      case StickyNotePresentInCurrent(id, _)    => s"Note '$id' removed"
      case PropertiesDifferent(_, _)            => "Properties modified"
    }.toList

  private def isLayoutOnlyNodeDiff(current: NodeData, other: NodeData): Boolean =
    withoutLayoutData(current) == withoutLayoutData(other)

  private def isLayoutOnlyStickyNoteDiff(current: StickyNote, other: StickyNote): Boolean =
    current.copy(additionalFields = withoutLayoutData(current.additionalFields)) ==
      other.copy(additionalFields = withoutLayoutData(other.additionalFields))

  private def withoutLayoutData(
      additionalFields: Option[UserDefinedAdditionalNodeFields]
  ): Option[UserDefinedAdditionalNodeFields] =
    additionalFields.map(_.copy(layoutData = None))

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
    case n: BranchEndData => n
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
