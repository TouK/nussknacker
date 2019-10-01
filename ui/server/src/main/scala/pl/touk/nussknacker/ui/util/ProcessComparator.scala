package pl.touk.nussknacker.ui.util

import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess

object ProcessComparator {

  def compare(currentProcess: DisplayableProcess, otherProcess: DisplayableProcess) : Map[String, Difference] = {
    val currentIds = currentProcess.nodes.map(_.id).toSet
    val otherIds = otherProcess.nodes.map(_.id).toSet

    (currentIds ++ otherIds).map(id => (currentProcess.nodes.find(_.id == id), otherProcess.nodes.find(_.id == id))).collect {
      case (Some(node), None) => NodeNotPresentInOther(node.id, node)
      case (None, Some(node)) => NodeNotPresentInCurrent(node.id, node)
      case (Some(currentNode), Some(otherNode)) if currentNode != otherNode => NodeDifferent(currentNode.id, currentNode, otherNode)
    }.map(difference => difference.id -> difference).toMap

  }

  import pl.touk.nussknacker.restmodel.NodeDataCodec.nodeDataEncoder
  import pl.touk.nussknacker.restmodel.NodeDataCodec.nodeDataDecoder
  import pl.touk.nussknacker.engine.api.CirceUtil._

  @ConfiguredJsonCodec sealed trait Difference {
    def id: String
  }

  sealed trait NodeDifference extends Difference {
    def nodeId: String
    override def id : String = nodeId
  }

  case class NodeDifferent(nodeId: String, currentNode: NodeData, otherNode: NodeData) extends NodeDifference

  case class NodeNotPresentInOther(nodeId: String, currentNode: NodeData) extends NodeDifference

  case class NodeNotPresentInCurrent(nodeId: String, otherNode: NodeData) extends NodeDifference

  /* TODO: implement rest...
  case class PropertiesDifferent(current: ProcessProperties, other: ProcessProperties, differences: Set[NodeDifference])
    extends Difference

  case class EdgeNotPresentInOther(fromId: String, toId: String) extends Difference

  case class EdgeNotPresentInCurrent(fromId: String, toId: String) extends Difference

  case class DifferentEdgeTypes(fromId: String, toId: String, currentEdgeType: EdgeType, otherEdgeType: EdgeType) extends Difference
                         */

}
