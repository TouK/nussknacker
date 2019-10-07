package pl.touk.nussknacker.ui.process.marshall

import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode._
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType.SubprocessOutput
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.{Edge, EdgeType}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties, displayablenode}

object ProcessConverter {
  def toCanonicalOrDie(canonicalJson: String) : CanonicalProcess = {
    ProcessMarshaller.fromJson(canonicalJson) match {
      case Valid(canonical) => canonical
      case Invalid(err) => throw new IllegalArgumentException(err.msg + "\n" + canonicalJson)
    }
  }

  def toDisplayableOrDie(canonicalJson: String, processingType: ProcessingType, businessView: Boolean = false): DisplayableProcess = {
    toDisplayable(toCanonicalOrDie(canonicalJson), processingType, businessView = businessView)
  }

  def modify(canonicalJson: String, processingType: ProcessingType)(f: DisplayableProcess => DisplayableProcess): String = {
    val displayable = ProcessConverter.toDisplayableOrDie(canonicalJson, processingType)
    val modified = f(displayable)
    val canonical = ProcessConverter.fromDisplayable(modified)
    ProcessMarshaller.toJson(canonical).spaces2
  }

  //FIXME: without default param
  def toDisplayable(process: CanonicalProcess, processingType: ProcessingType, businessView: Boolean = false): DisplayableProcess = {
    val (nodes, edges) = if (businessView) {
      toGraphInner(process.nodes.flatMap(toBusinessNode))
    } else {
      (process.nodes :: process.additionalBranches.getOrElse(Nil)).map(toGraphInner)
          .reduceLeft[(List[NodeData], List[Edge])] {
            case ((n1, e1), (n2, e2)) => (n1 ++ n2, e1 ++ e2)
          }
    }
    val props = ProcessProperties(
      typeSpecificProperties = process.metaData.typeSpecificData,
      exceptionHandler = process.exceptionHandlerRef,
      isSubprocess = process.metaData.isSubprocess,
      additionalFields = process.metaData.additionalFields,
      subprocessVersions = process.metaData.subprocessVersions
    )
    DisplayableProcess(process.metaData.id, props, nodes, edges, processingType)
  }

  private def toBusinessNode(canonicalNode: CanonicalNode): Option[CanonicalNode] = {
    canonicalNode match {
      case canonicalnode.FlatNode(data) => data match {
        case _: Enricher => None
        case _: VariableBuilder => None
        case _: Variable => None
        case _ => Some(canonicalNode)
      }
      case canonicalnode.FilterNode(data, nexts) =>
        val newNexts = nexts.flatMap(toBusinessNode)
        Some(canonicalnode.FilterNode(data, newNexts))
      case canonicalnode.SwitchNode(data, nexts, defaultNext) =>
        val newNodes = nexts.map(c => c.copy(nodes = c.nodes.flatMap(toBusinessNode)))
        val newDefaults = defaultNext.flatMap(toBusinessNode)
        Some(canonicalnode.SwitchNode(data, newNodes, newDefaults))
      case canonicalnode.SplitNode(data, nexts) =>
        val newNexts = nexts.map(_.flatMap(toBusinessNode))
        Some(canonicalnode.SplitNode(data, newNexts))
      case canonicalnode.Subprocess(data, outputs) =>
        val newOutputs = outputs.mapValues(_.flatMap(toBusinessNode)).filter(_._2.nonEmpty)
        Some(canonicalnode.Subprocess(data, newOutputs))
    }
  }

  def findNodes(process: CanonicalProcess) : List[NodeData] = toGraphInner(process.nodes)._1

  private def toGraphInner(nodes: List[canonicalnode.CanonicalNode]): (List[NodeData], List[displayablenode.Edge]) =
    nodes match {
      case canonicalnode.FlatNode(BranchEndData(_, _)) :: _ => (List(), List())
      case canonicalnode.FlatNode(data) :: tail =>
        val (tailNodes, tailEdges) = toGraphInner(tail)
        (data :: tailNodes, createNextEdge(data.id, tail) ::: tailEdges)
      case canonicalnode.FilterNode(data, nextFalse) :: tail =>
        val (nextFalseNodes, nextFalseEdges) = toGraphInner(nextFalse)
        val nextFalseEdgesConnectedToFilter = nextFalseNodes match {
          case Nil => nextFalseEdges
          case h :: _ => displayablenode.Edge(data.id, h.id, Some(EdgeType.FilterFalse)) :: nextFalseEdges
        }
        val (tailNodes, tailEdges) = toGraphInner(tail)
        (data :: nextFalseNodes ::: tailNodes, createNextEdge(data.id, tail, Some(EdgeType.FilterTrue)) ::: nextFalseEdgesConnectedToFilter ::: tailEdges)
      case canonicalnode.SwitchNode(data, nexts, defaultNext) :: tail =>
        val (defaultNextNodes, defaultNextEdges) = toGraphInner(defaultNext)
        val defaultNextEdgesConnectedToSwitch = defaultNextNodes match {
          case Nil => defaultNextEdges
          case h :: _ => displayablenode.Edge(data.id, h.id, Some(EdgeType.SwitchDefault)) :: defaultNextEdges
        }
        val (tailNodes, tailEdges) = toGraphInner(tail)
        val (nextNodes, nextEdges) = unzipListTuple(nexts.map { c =>
          val (nextNodeNodes, nextNodeEdges) = toGraphInner(c.nodes)
          (nextNodeNodes, nextNodeNodes.headOption.map(n => displayablenode.Edge(data.id, n.id, Some(EdgeType.NextSwitch(c.expression)))).toList ::: nextNodeEdges)
        })
        (data :: defaultNextNodes ::: nextNodes ::: tailNodes, createNextEdge(data.id, tail) ::: nextEdges ::: defaultNextEdgesConnectedToSwitch ::: tailEdges)
      case canonicalnode.SplitNode(data, nexts) :: tail =>
        val (tailNodes, tailEdges) = toGraphInner(tail)
        val nextInner = nexts.map(toGraphInner).unzip
        val nodes = nextInner._1.flatten
        val edges = nextInner._2.flatten
        val connecting = nexts.flatMap(e => e.headOption.map(_.id).map(displayablenode.Edge(data.id, _, None)))
        (data :: nodes ::: tailNodes, connecting ::: edges ::: tailEdges)
      case canonicalnode.Subprocess(data, outputs) :: tail =>
        val (tailNodes, tailEdges) = toGraphInner(tail)
        val nextInner = outputs.values.toList.map(toGraphInner).unzip
        val nodes = nextInner._1.flatten
        val edges = nextInner._2.flatten
        val connecting = outputs
          .flatMap{ case (name, outputEdges) =>
            outputEdges.headOption.map(_.id).map(displayablenode.Edge(data.id, _, Some(SubprocessOutput(name))))}.toList
        (data :: nodes ::: tailNodes, connecting ::: edges ::: tailEdges)
      case Nil =>
        (List(),List())
    }

  private def createNextEdge(id: String, tail: List[CanonicalNode], edgeType: Option[EdgeType] = None): List[displayablenode.Edge] = {
    tail.headOption.map {
      case FlatNode(BranchEndData(_, BranchEndDefinition(_, joinId))) => displayablenode.Edge(id, joinId, None)
      case n => displayablenode.Edge(id, n.id, edgeType)
    }.toList
  }

  private def unzipListTuple[A, B](a: List[(List[A], List[B])]): (List[A], List[B]) = {
    val (aList, bList) = a.unzip
    (aList.flatten, bList.flatten)
  }

  def fromDisplayable(process: DisplayableProcess): CanonicalProcess = {
    val nodesMap = process.nodes.groupBy(_.id).mapValues(_.head)
    val edgesFromMapStart = process.edges.groupBy(_.from)
    val rootsUnflattened = findRootNodes(process).map(headNode => unFlattenNode(nodesMap, None)(headNode, edgesFromMapStart))
    val nodes = rootsUnflattened.headOption.getOrElse(List())
    CanonicalProcess(process.metaData, process.properties.exceptionHandler, nodes, if (rootsUnflattened.isEmpty) None else Some(rootsUnflattened.tail))
  }

  private def findRootNodes(process: DisplayableProcess): List[NodeData] =
    process.nodes.filter(n => n.isInstanceOf[StartingNodeData])

  private def unFlattenNode(nodesMap: Map[String, NodeData], stopAtJoin: Option[Edge])
                           (n: NodeData, edgesFromMap: Map[String, List[displayablenode.Edge]]): List[canonicalnode.CanonicalNode] = {
    def unflattenEdgeEnd(id: String, e: displayablenode.Edge): List[canonicalnode.CanonicalNode] = {
      unFlattenNode(nodesMap, Some(e))(nodesMap(e.to), edgesFromMap.updated(id, edgesFromMap(id).filterNot(_ == e)))
    }

    def getEdges(id: String): List[Edge] = edgesFromMap.getOrElse(id, List())

    val handleNestedNodes: PartialFunction[(NodeData, Option[Edge]), List[canonicalnode.CanonicalNode]] = {
      case (data: Filter, _) =>
        val filterEdges = getEdges(data.id)
        val next = filterEdges.find(_.edgeType.contains(EdgeType.FilterTrue)).map(truePath => unflattenEdgeEnd(data.id, truePath)).getOrElse(List())
        val nextFalse = filterEdges.find(_.edgeType.contains(EdgeType.FilterFalse)).map(nf => unflattenEdgeEnd(data.id, nf)).toList.flatten
        canonicalnode.FilterNode(data, nextFalse) :: next
      case (data: Switch, _) =>
        val nexts = getEdges(data.id).collect { case e@displayablenode.Edge(_, _, Some(EdgeType.NextSwitch(edgeExpr))) =>
          canonicalnode.Case(edgeExpr, unflattenEdgeEnd(data.id, e))
        }
        val default = getEdges(data.id).find(_.edgeType.contains(EdgeType.SwitchDefault)).map { e =>
          unflattenEdgeEnd(data.id, e)
        }.toList.flatten
        canonicalnode.SwitchNode(data, nexts, default) :: Nil
      case (data: Split, _) =>
        val nexts = getEdges(data.id).map(unflattenEdgeEnd(data.id, _))
        canonicalnode.SplitNode(data, nexts) :: Nil
      case (data: SubprocessInput, _) =>
        //TODO error handling?
        val nexts = getEdges(data.id).map(e => e.edgeType.get.asInstanceOf[SubprocessOutput].name -> unflattenEdgeEnd(data.id, e)).toMap
        canonicalnode.Subprocess(data, nexts) :: Nil
      case (data: Join, Some(edgeConnectedToJoin)) =>
        // We are using "from" node's id as a branchId because for now branchExpressions are inside Join nodes and it is convenient
        // way to connect both two things.
        val joinId = edgeConnectedToJoin.from
        canonicalnode.FlatNode(BranchEndData(s"$$edge-${edgeConnectedToJoin.from}-${edgeConnectedToJoin.to}", BranchEndDefinition(joinId, data.id))) :: Nil

    }
    (handleNestedNodes orElse (handleDirectNodes andThen { n =>
      n :: getEdges(n.id).flatMap(unflattenEdgeEnd(n.id, _))
    }))((n, stopAtJoin))
  }

  private val handleDirectNodes: PartialFunction[(NodeData, Option[Edge]), canonicalnode.CanonicalNode] = {
    case (data, _) => FlatNode(data)
  }

}