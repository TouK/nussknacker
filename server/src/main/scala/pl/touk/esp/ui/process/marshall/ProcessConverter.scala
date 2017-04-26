package pl.touk.esp.ui.process.marshall

import cats.data.Validated.{Invalid, Valid}
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.canonicalgraph.canonicalnode._
import pl.touk.esp.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.esp.ui.process.displayedgraph.displayablenode.EdgeType.SubprocessOutput
import pl.touk.esp.ui.process.displayedgraph.displayablenode.{Edge, EdgeType}
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties, displayablenode}

object ProcessConverter {

  val processMarshaller = UiProcessMarshaller()

  def toDisplayableOrDie(canonicalJson: String, processingType: ProcessingType): DisplayableProcess = {
    processMarshaller.fromJson(canonicalJson) match {
      case Valid(canonical) => toDisplayable(canonical, processingType)
      case Invalid(err) => throw new IllegalArgumentException(err.msg + "\n" + canonicalJson)
    }
  }

  def toDisplayable(process: CanonicalProcess, processingType: ProcessingType): DisplayableProcess = {
    val nodesEdges = toGraphInner(process.nodes)
    val (nodes, edges) = nodesEdges

    val props = ProcessProperties(process.metaData.typeSpecificData, process.exceptionHandlerRef, process.metaData.additionalFields)
    DisplayableProcess(process.metaData.id, props, nodes, edges, processingType)
  }

  private def toGraphInner(nodes: List[canonicalnode.CanonicalNode]): (List[NodeData], List[displayablenode.Edge]) =
    nodes match {
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
    tail.headOption.map(n => displayablenode.Edge(id, n.id, edgeType)).toList
  }

  private def unzipListTuple[A, B](a: List[(List[A], List[B])]): (List[A], List[B]) = {
    val (aList, bList) = a.unzip
    (aList.flatten, bList.flatten)
  }

  def fromDisplayable(process: DisplayableProcess): CanonicalProcess = {
    val nodesMap = process.nodes.groupBy(_.id).mapValues(_.head)
    val edgesFromMapStart = process.edges.groupBy(_.from)
    //FIXME: co z luznymi wezlami???
    val nodes = findRootNodes(process).headOption.map(headNode => unFlattenNode(nodesMap)(headNode, edgesFromMapStart)).getOrElse(List())
    CanonicalProcess(process.metaData, process.properties.exceptionHandler, nodes)
  }

  private def findRootNodes(process: DisplayableProcess): List[NodeData] =
    process.nodes.filterNot(n => process.edges.exists(_.to == n.id))

  private def unFlattenNode(nodesMap: Map[String, NodeData])
                           (n: NodeData, edgesFromMap: Map[String, List[displayablenode.Edge]]): List[canonicalnode.CanonicalNode] = {
    def unflattenEdgeEnd(id: String, e: displayablenode.Edge): List[canonicalnode.CanonicalNode] = {
      unFlattenNode(nodesMap)(nodesMap(e.to), edgesFromMap.updated(id, edgesFromMap(id).filterNot(_ == e)))
    }

    def getEdges(id: String): List[Edge] = edgesFromMap.getOrElse(id, List())

    val handleNestedNodes: PartialFunction[NodeData, List[canonicalnode.CanonicalNode]] = {
      case data: Filter =>
        val filterEdges = getEdges(data.id)
        val next = filterEdges.find(_.edgeType.contains(EdgeType.FilterTrue)).map(truePath => unflattenEdgeEnd(data.id, truePath)).getOrElse(List())
        val nextFalse = filterEdges.find(_.edgeType.contains(EdgeType.FilterFalse)).map(nf => unflattenEdgeEnd(data.id, nf)).toList.flatten
        canonicalnode.FilterNode(data, nextFalse) :: next
      case data: Switch =>
        val nexts = getEdges(data.id).collect { case e@displayablenode.Edge(_, _, Some(EdgeType.NextSwitch(edgeExpr))) =>
          canonicalnode.Case(edgeExpr, unflattenEdgeEnd(data.id, e))
        }
        val default = getEdges(data.id).find(_.edgeType.contains(EdgeType.SwitchDefault)).map { e =>
          unflattenEdgeEnd(data.id, e)
        }.toList.flatten
        canonicalnode.SwitchNode(data, nexts, default) :: Nil
      case data: Split =>
        val nexts = getEdges(data.id).map(unflattenEdgeEnd(data.id, _))
        canonicalnode.SplitNode(data, nexts) :: Nil
      case data: SubprocessInput =>
        //TODO: obsluga jakis bledow?
        val nexts = getEdges(data.id).map(e => e.edgeType.get.asInstanceOf[SubprocessOutput].name -> unflattenEdgeEnd(data.id, e)).toMap
        canonicalnode.Subprocess(data, nexts) :: Nil

    }
    (handleNestedNodes orElse (handleDirectNodes andThen { n =>
      n :: getEdges(n.id).flatMap(unflattenEdgeEnd(n.id, _))
    }))(n)
  }

  def nodeFromDisplayable(n: NodeData): canonicalnode.CanonicalNode = {
    val handleNestedNodes: PartialFunction[NodeData, canonicalnode.CanonicalNode] = {
      case data: Filter =>
        canonicalnode.FilterNode(data, List.empty)
      case data: Switch =>
        canonicalnode.SwitchNode(data, List.empty, List.empty)
    }
    (handleNestedNodes orElse handleDirectNodes)(n)
  }

  private val handleDirectNodes: PartialFunction[NodeData, canonicalnode.CanonicalNode] = {
    case data => FlatNode(data)
  }

}