package pl.touk.nussknacker.ui.process.marshall

import pl.touk.nussknacker.engine.api.graph
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.{canonicalnode, CanonicalProcess}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode._
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.EdgeType.FragmentOutput
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

object CanonicalProcessConverter {

  def toScenarioGraph(process: CanonicalProcess): ScenarioGraph = {
    val (nodes, edges) = {
      process.allStartNodes
        .map(toGraphInner)
        .reduceLeft[(List[NodeData], List[Edge])] { case ((n1, e1), (n2, e2)) =>
          (n1 ++ n2, e1 ++ e2)
        }
    }
    val props = ProcessProperties(process.metaData.additionalFields)
    ScenarioGraph(props, nodes, edges)
  }

  def findNodes(process: CanonicalProcess): List[NodeData] = {
    process.allStartNodes.toList.flatMap(branch => toGraphInner(branch)._1)
  }

  private def toGraphInner(nodes: List[canonicalnode.CanonicalNode]): (List[NodeData], List[Edge]) =
    nodes match {
      case canonicalnode.FlatNode(BranchEndData(_)) :: _ => (List(), List())
      case canonicalnode.FlatNode(data) :: tail =>
        val (tailNodes, tailEdges) = toGraphInner(tail)
        (data :: tailNodes, createNextEdge(data.id, tail) ::: tailEdges)
      case canonicalnode.FilterNode(data, nextFalse) :: tail =>
        val (nextFalseNodes, nextFalseEdges) = toGraphInner(nextFalse)
        val nextFalseEdgesConnectedToFilter =
          createNextEdge(data.id, nextFalse, Some(EdgeType.FilterFalse)) ::: nextFalseEdges
        val (tailNodes, tailEdges) = toGraphInner(tail)
        (
          data :: nextFalseNodes ::: tailNodes,
          createNextEdge(data.id, tail, Some(EdgeType.FilterTrue)) ::: nextFalseEdgesConnectedToFilter ::: tailEdges
        )
      case canonicalnode.SwitchNode(data, nexts, defaultNext) :: tail =>
        val (defaultNextNodes, defaultNextEdges) = toGraphInner(defaultNext)
        val defaultNextEdgesConnectedToSwitch =
          createNextEdge(data.id, defaultNext, Some(EdgeType.SwitchDefault)) ::: defaultNextEdges
        val (tailNodes, tailEdges) = toGraphInner(tail)
        val (nextNodes, nextEdges) = unzipListTuple(nexts.map { c =>
          val (nextNodeNodes, nextNodeEdges) = toGraphInner(c.nodes)
          (nextNodeNodes, createNextEdge(data.id, c.nodes, Some(EdgeType.NextSwitch(c.expression))) ::: nextNodeEdges)
        })
        (
          data :: defaultNextNodes ::: nextNodes ::: tailNodes,
          createNextEdge(data.id, tail) ::: nextEdges ::: defaultNextEdgesConnectedToSwitch ::: tailEdges
        )
      case canonicalnode.SplitNode(data, nexts) :: tail =>
        val (tailNodes, tailEdges) = toGraphInner(tail)
        val nextInner              = nexts.map(toGraphInner).unzip
        val nodes                  = nextInner._1.flatten
        val edges                  = nextInner._2.flatten
        val connecting             = nexts.flatMap(createNextEdge(data.id, _, None))
        (data :: nodes ::: tailNodes, connecting ::: edges ::: tailEdges)
      case canonicalnode.Fragment(data, outputs) :: tail =>
        val (tailNodes, tailEdges) = toGraphInner(tail)
        val nextInner              = outputs.values.toList.map(toGraphInner).unzip
        val nodes                  = nextInner._1.flatten
        val edges                  = nextInner._2.flatten
        val connecting = outputs.flatMap { case (name, outputEdges) =>
          createNextEdge(data.id, outputEdges, Some(FragmentOutput(name)))
        }.toList
        (data :: nodes ::: tailNodes, connecting ::: edges ::: tailEdges)
      case Nil =>
        (List(), List())
    }

  private def createNextEdge(
      id: String,
      tail: List[CanonicalNode],
      edgeType: Option[EdgeType] = None
  ): List[Edge] = {
    tail.headOption.map {
      case FlatNode(BranchEndData(BranchEndDefinition(_, joinId))) => graph.Edge(id, joinId, edgeType)
      case n                                                       => graph.Edge(id, n.id, edgeType)
    }.toList
  }

  private def unzipListTuple[A, B](a: List[(List[A], List[B])]): (List[A], List[B]) = {
    val (aList, bList) = a.unzip
    (aList.flatten, bList.flatten)
  }

  def fromScenarioGraph(graph: ScenarioGraph, name: ProcessName): CanonicalProcess = {
    val nodesMap          = graph.nodes.groupBy(_.id).mapValuesNow(_.head)
    val edgesFromMapStart = graph.edges.groupBy(_.from)
    val rootsUnflattened =
      findRootNodes(graph).map(headNode => unFlattenNode(nodesMap, None)(headNode, edgesFromMapStart))
    val nodes              = rootsUnflattened.headOption.getOrElse(List.empty)
    val additionalBranches = if (rootsUnflattened.isEmpty) List.empty else rootsUnflattened.tail
    CanonicalProcess(graph.toMetaData(name), nodes, additionalBranches)
  }

  // TODO: Should find root nodes based no structure to have loose nodes visible at canonical process level - otherwise
  //  we need to fail fast on loose nodes before calling converter
  private def findRootNodes(process: ScenarioGraph): List[NodeData] =
    process.nodes.filter(n => n.isInstanceOf[StartingNodeData])

  private def unFlattenNode(
      nodesMap: Map[String, NodeData],
      stopAtJoin: Option[Edge]
  )(n: NodeData, edgesFromMap: Map[String, List[Edge]]): List[canonicalnode.CanonicalNode] = {
    def unflattenEdgeEnd(id: String, e: Edge): List[canonicalnode.CanonicalNode] = {
      unFlattenNode(nodesMap, Some(e))(nodesMap(e.to), edgesFromMap.updated(id, edgesFromMap(id).filterNot(_ == e)))
    }

    def getEdges(id: String): List[Edge] = edgesFromMap.getOrElse(id, List())

    val handleNestedNodes: PartialFunction[(NodeData, Option[Edge]), List[canonicalnode.CanonicalNode]] = {
      case (data: Filter, _) =>
        val filterEdges = getEdges(data.id)
        val next = filterEdges
          .find(_.edgeType.contains(EdgeType.FilterTrue))
          .map(truePath => unflattenEdgeEnd(data.id, truePath))
          .getOrElse(List())
        val nextFalse = filterEdges
          .find(_.edgeType.contains(EdgeType.FilterFalse))
          .map(nf => unflattenEdgeEnd(data.id, nf))
          .toList
          .flatten
        canonicalnode.FilterNode(data, nextFalse) :: next
      case (data: Switch, _) =>
        val nexts = getEdges(data.id).collect { case e @ Edge(_, _, Some(EdgeType.NextSwitch(edgeExpr))) =>
          canonicalnode.Case(edgeExpr, unflattenEdgeEnd(data.id, e))
        }
        val default = getEdges(data.id)
          .find(_.edgeType.contains(EdgeType.SwitchDefault))
          .map { e =>
            unflattenEdgeEnd(data.id, e)
          }
          .toList
          .flatten
        canonicalnode.SwitchNode(data, nexts, default) :: Nil
      case (data: Split, _) =>
        val nexts = getEdges(data.id).map(unflattenEdgeEnd(data.id, _))
        canonicalnode.SplitNode(data, nexts) :: Nil
      case (data: FragmentInput, _) =>
        // TODO error handling?
        val nexts = getEdges(data.id)
          .map(e => e.edgeType.get.asInstanceOf[FragmentOutput].name -> unflattenEdgeEnd(data.id, e))
          .toMap
        canonicalnode.Fragment(data, nexts) :: Nil
      case (data: Join, Some(edgeConnectedToJoin)) =>
        // We are using "from" node's id as a branchId because for now branchExpressions are inside Join nodes and it is convenient
        // way to connect both two things.
        val joinId = edgeConnectedToJoin.from
        canonicalnode.FlatNode(BranchEndData(BranchEndDefinition(joinId, data.id))) :: Nil

    }
    (handleNestedNodes orElse (handleDirectNodes andThen { n =>
      n :: getEdges(n.id).flatMap(unflattenEdgeEnd(n.id, _))
    }))((n, stopAtJoin))
  }

  private val handleDirectNodes: PartialFunction[(NodeData, Option[Edge]), canonicalnode.CanonicalNode] = {
    case (data, _) => FlatNode(data)
  }

}
