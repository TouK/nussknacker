package pl.touk.nussknacker.engine.canonicalgraph

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.{graph, NodeId}
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode._
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.EdgeType.FragmentOutput
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

object CanonicalProcessConverter extends LazyLogging {

  private[canonicalgraph] def toScenarioGraph(process: CanonicalProcess): ScenarioGraph = {
    val (nodes, edges) = {
      process.allStartNodes
        .map(toGraphInner)
        .reduceLeft[(List[NodeData], List[Edge])] { case ((n1, e1), (n2, e2)) =>
          (n1 ++ n2, e1 ++ e2)
        }
    }
    val props = ProcessProperties(process.metaData.additionalFields)
    ScenarioGraph(props, nodes, edges, process.stickyNotes, process.testCases)
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
      case canonicalnode.CustomNodeWithOutputs(data, outputs) :: tail =>
        val (tailNodes, tailEdges) = toGraphInner(tail)
        val nextInner              = outputs.toList.map(output => toGraphInner(output.nodes)).unzip
        val nodes                  = nextInner._1.flatten
        val edges                  = nextInner._2.flatten
        val connecting = outputs.toList.flatMap { output =>
          createNextEdge(data.id, output.nodes, Some(EdgeType.CustomNodeOutput(output.name)))
        }
        (
          data :: nodes ::: tailNodes,
          createNextEdge(data.id, tail) ::: connecting ::: edges ::: tailEdges
        )
      case Nil =>
        (List(), List())
    }

  private def createNextEdge(
      id: NodeId,
      tail: List[CanonicalNode],
      edgeType: Option[EdgeType] = None
  ): List[Edge] = {
    tail.headOption.map {
      case FlatNode(BranchEndData(BranchEndDefinition(_, joinId))) => graph.Edge(id, NodeId(joinId), edgeType)
      case n                                                       => graph.Edge(id, n.id, edgeType)
    }.toList
  }

  private def unzipListTuple[A, B](a: List[(List[A], List[B])]): (List[A], List[B]) = {
    val (aList, bList) = a.unzip
    (aList.flatten, bList.flatten)
  }

  /**
    * Legacy `ScenarioGraph` payloads may reference a node by name where an id is expected (e.g. in `Edge.from`).
    * Exposed so that validations running *before* `fromScenarioGraph` see the same graph the conversion will.
    */
  private[nussknacker] def nodeIdNormalizer(nodes: List[NodeData]): NodeId => NodeId = {
    val nodeIds = nodes.map(_.id).toSet
    val nodeIdAliasesByName = nodes.foldLeft(Map.empty[NodeId, NodeId]) { case (acc, node) =>
      val alias = NodeId(node.name.value)
      if (nodeIds.contains(alias)) acc else acc + (alias -> node.id)
    }
    nodeId => nodeIdAliasesByName.getOrElse(nodeId, nodeId)
  }

  private def isNamedOutputEdge(e: Edge): Boolean =
    e.edgeType.exists(_.isInstanceOf[EdgeType.CustomNodeOutput])

  def fromScenarioGraph(graph: ScenarioGraph, name: ProcessName): CanonicalProcess = {
    val rawNodesMap     = graph.nodes.groupBy(_.id).mapValuesNow(_.head)
    val normalizeNodeId = nodeIdNormalizer(graph.nodes)

    def normalizeJoinBranchId(branchId: String): String = {
      val normalizedBranchNodeId = normalizeNodeId(NodeId(branchId))
      rawNodesMap.get(normalizedBranchNodeId).map(_.name.value).getOrElse(branchId)
    }

    val nodesMap = graph.nodes
      .map {
        case join: Join =>
          join.copy(
            branchParameters = join.branchParameters.map(bp => bp.copy(branchId = normalizeJoinBranchId(bp.branchId)))
          )
        case other => other
      }
      .groupBy(_.id)
      .mapValuesNow(_.head)

    val normalizedEdges =
      graph.edges.map(edge => edge.copy(from = normalizeNodeId(edge.from), to = normalizeNodeId(edge.to)))
    val edgesFromMapStart = normalizedEdges.groupBy(_.from)
    val rootsUnflattened =
      findRootNodes(graph).map(headNode =>
        unFlattenNode(nodesMap, None, name)(nodesMap(headNode.id), edgesFromMapStart)
      )
    val nodes              = rootsUnflattened.headOption.getOrElse(List.empty)
    val additionalBranches = if (rootsUnflattened.isEmpty) List.empty else rootsUnflattened.tail
    CanonicalProcess(graph.toMetaData(name), nodes, additionalBranches, graph.stickyNotes, graph.testCases)
  }

  // TODO: Should find root nodes based no structure to have loose nodes visible at canonical process level - otherwise
  //  we need to fail fast on loose nodes before calling converter
  private def findRootNodes(process: ScenarioGraph): List[NodeData] =
    process.nodes.filter(n => n.isInstanceOf[StartingNodeData])

  private def unFlattenNode(
      nodesMap: Map[NodeId, NodeData],
      stopAtJoin: Option[Edge],
      scenarioName: ProcessName
  )(n: NodeData, edgesFromMap: Map[NodeId, List[Edge]]): List[canonicalnode.CanonicalNode] = {
    def nodeOrThrow(nodeId: NodeId): NodeData =
      nodesMap.getOrElse(nodeId, throw new IllegalArgumentException(s"Cannot find node for id: ${nodeId.value}"))

    def unflattenEdgeEnd(id: NodeId, e: Edge): List[canonicalnode.CanonicalNode] = {
      unFlattenNode(nodesMap, Some(e), scenarioName)(
        nodeOrThrow(e.to),
        edgesFromMap.updated(id, edgesFromMap.getOrElse(id, List()).filterNot(_ == e))
      )
    }

    def getEdges(id: NodeId): List[Edge] = edgesFromMap.getOrElse(id, List())

    def warnDroppedEdges(data: NodeData, reason: String, dropped: List[Edge]): Unit =
      if (dropped.nonEmpty) {
        val descriptions =
          dropped.map(e => s"${e.edgeType.map(_.toString).getOrElse("unnamed output")} -> ${e.to.value}")
        logger.warn(
          s"Node '${data.id.value}' in scenario '${scenarioName.value}' $reason: ${descriptions.mkString(", ")}. " +
            "Dropping them and their downstream nodes."
        )
      }

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
        val branchId = nodeOrThrow(edgeConnectedToJoin.from).name.value
        canonicalnode.FlatNode(BranchEndData(BranchEndDefinition(branchId, data.id.value))) :: Nil

      case (data: CustomNode, _) if getEdges(data.id).exists(isNamedOutputEdge) =>
        val (namedOutputEdges, remainingEdges) = getEdges(data.id).partitionMap {
          case e @ Edge(_, _, Some(EdgeType.CustomNodeOutput(name))) => Left(name -> e)
          case e                                                     => Right(e)
        }
        // One subgraph per output name: the first edge under a name wins, the rest are surplus.
        val keptOutputEdges      = namedOutputEdges.distinctBy { case (name, _) => name }
        val duplicateOutputEdges = namedOutputEdges.diff(keptOutputEdges)
        // Every non-named edge is dropped with its subgraph - the validator rejects such a mix, the warn covers
        // programmatic input.
        warnDroppedEdges(
          data,
          "has outgoing edges that cannot be represented next to its named outputs",
          remainingEdges ++ duplicateOutputEdges.map { case (_, e) => e }
        )
        val outputs = keptOutputEdges.map { case (name, e) => canonicalnode.Output(name, unflattenEdgeEnd(data.id, e)) }
        canonicalnode.CustomNodeWithOutputs(data, outputs) :: Nil

    }
    // A "direct" (one-output) node: every outgoing edge is the normal continuation, flattened inline.
    // A CustomNodeOutput edge on a direct node is only reachable through import/API and is dropped here.
    val handleDirectNode: PartialFunction[(NodeData, Option[Edge]), List[canonicalnode.CanonicalNode]] = {
      case (data, _) =>
        val (outputEdges, edges) = getEdges(data.id).partition(isNamedOutputEdge)
        warnDroppedEdges(data, "cannot have named outputs, but has outgoing edges", outputEdges)
        canonicalnode.FlatNode(data) :: edges.flatMap(unflattenEdgeEnd(data.id, _))
    }
    (handleNestedNodes orElse handleDirectNode)((n, stopAtJoin))
  }

}
