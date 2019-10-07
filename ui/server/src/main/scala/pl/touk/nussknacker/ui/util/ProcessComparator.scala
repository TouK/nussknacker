package pl.touk.nussknacker.ui.util

import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge

object ProcessComparator {
  def compare(currentProcess: DisplayableProcess, otherProcess: DisplayableProcess) : Map[String, Difference] = {
    val nodes = getDifferences(
      currentProcess.nodes.map(node => node.id -> node).toMap,
      otherProcess.nodes.map(node => node.id -> node).toMap
    )(
      notPresentInOther = current => NodeNotPresentInOther(current.id, current),
      notPresentInCurrent = other => NodeNotPresentInCurrent(other.id, other),
      different = (current, other) => NodeDifferent(current.id, current, other)
    )

    val edges = getDifferences(
      currentProcess.edges.map(edge => (edge.from, edge.to) -> edge).toMap,
      otherProcess.edges.map(edge => (edge.from, edge.to) -> edge).toMap
    )(
      notPresentInOther = current => EdgeNotPresentInOther(current.from, current.to, current),
      notPresentInCurrent = other => EdgeNotPresentInCurrent(other.from, other.to, other),
      different = (current, other) => EdgeDifferent(current.from, current.to, current, other)
    )

    nodes ++ edges
  }

  private def getDifferences[K, V](currents: Map[K, V], others: Map[K, V])
                                  (notPresentInOther: V => Difference,
                                   notPresentInCurrent: V => Difference,
                                   different: (V, V) => Difference): Map[String, Difference] = {
    (currents.keys ++ others.keys)
      .toSet
      .map((id: K) => (currents.get(id), others.get(id)))
      .collect {
        case (Some(current), None) => notPresentInOther(current)
        case (None, Some(other)) => notPresentInCurrent(other)
        case (Some(current), Some(other)) if current != other => different(current, other)
      }
      .map(difference => difference.id -> difference)
      .toMap
  }

  import pl.touk.nussknacker.engine.graph.NodeDataCodec._
  import pl.touk.nussknacker.engine.api.CirceUtil._

  @ConfiguredJsonCodec sealed trait Difference {
    def id: String
  }

  sealed trait NodeDifference extends Difference {
    def nodeId: String

    override def id: String = s"Node '$nodeId'"
  }

  case class NodeDifferent(nodeId: String, currentNode: NodeData, otherNode: NodeData) extends NodeDifference

  case class NodeNotPresentInOther(nodeId: String, currentNode: NodeData) extends NodeDifference

  case class NodeNotPresentInCurrent(nodeId: String, otherNode: NodeData) extends NodeDifference

  sealed trait EdgeDifference extends Difference {
    def fromId: String
    def toId: String

    override def id : String = s"Edge from '$fromId' to '$toId'"
  }

  case class EdgeDifferent(fromId: String, toId: String, currentEdge: Edge, otherEdge: Edge) extends EdgeDifference

  case class EdgeNotPresentInOther(fromId: String, toId: String, currentEdge: Edge) extends EdgeDifference

  case class EdgeNotPresentInCurrent(fromId: String, toId: String, otherEdge: Edge) extends EdgeDifference

  /* TODO: implement rest...
  case class PropertiesDifferent(current: ProcessProperties, other: ProcessProperties, differences: Set[NodeDifference])
    extends Difference
  */
}
