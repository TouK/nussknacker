package pl.touk.nussknacker.ui.util

import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.graph.node.NodeData

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

    val properties = if (currentGraph.properties != otherGraph.properties) {
      PropertiesDifferent(currentGraph.properties, otherGraph.properties) :: Nil
    } else {
      Nil
    }

    nodes ++ edges ++ properties.map(property => property.id -> property).toMap
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

  import pl.touk.nussknacker.engine.api.CirceUtil._
  import pl.touk.nussknacker.engine.graph.node.NodeData._

  @ConfiguredJsonCodec sealed trait Difference {
    def id: String
  }

  sealed trait NodeDifference extends Difference {
    def nodeId: String

    override def id: String = s"Node '$nodeId'"
  }

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
