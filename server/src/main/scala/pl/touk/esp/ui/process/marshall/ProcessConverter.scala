package pl.touk.esp.ui.process.marshall

import cats.data.Validated.{Invalid, Valid}
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.canonicalgraph.canonicalnode._
import pl.touk.esp.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.esp.engine.graph.node.{Filter, NodeData, Split, Switch}
import pl.touk.esp.ui.api.ProcessValidation
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties, displayablenode}

class ProcessConverter(processValidation: ProcessValidation) {

  val processMarshaller = UiProcessMarshaller()

  def toDisplayableOrDie(canonicalJson: String): DisplayableProcess = {
    processMarshaller.fromJson(canonicalJson) match {
      case Valid(canonical) => toDisplayable(canonical)
      case Invalid(err) => throw new IllegalArgumentException(err.msg)
    }
  }

  def toDisplayable(process: CanonicalProcess): DisplayableProcess = {
    val nodesEdges = toGraphInner(process.nodes)
    val (nodes, edges) = nodesEdges
    val props = ProcessProperties(process.metaData.parallelism, process.metaData.splitStateToDisk, process.exceptionHandlerRef, process.metaData.additionalFields)
    DisplayableProcess(process.metaData.id, props, nodes, edges, processValidation.validate(process))
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
          case h :: _ => displayablenode.Edge(data.id, h.id, None) :: nextFalseEdges
        }
        val (tailNodes, tailEdges) = toGraphInner(tail)
        (data :: nextFalseNodes ::: tailNodes, createNextEdge(data.id, tail) ::: nextFalseEdgesConnectedToFilter ::: tailEdges)
      case canonicalnode.SwitchNode(data, nexts, defaultNext) :: tail =>
        val (defaultNextNodes, defaultNextEdges) = toGraphInner(defaultNext)
        val defaultNextEdgesConnectedToSwitch = defaultNextNodes match {
          case Nil => defaultNextEdges
          case h :: _ => displayablenode.Edge(data.id, h.id, None) :: defaultNextEdges
        }
        val (tailNodes, tailEdges) = toGraphInner(tail)
        val (nextNodes, nextEdges) = unzipListTuple(nexts.map { c =>
          val (nextNodeNodes, nextNodeEdges) = toGraphInner(c.nodes)
          (nextNodeNodes, nextNodeNodes.headOption.map(n => displayablenode.Edge(data.id, n.id, Some(c.expression))).toList ::: nextNodeEdges)
        })
        (data :: defaultNextNodes ::: nextNodes ::: tailNodes, createNextEdge(data.id, tail) ::: defaultNextEdgesConnectedToSwitch ::: nextEdges ::: tailEdges)
      case canonicalnode.SplitNode(data, nexts) :: tail =>
        val (tailNodes, tailEdges) = toGraphInner(tail)
        val nextInner = nexts.map(toGraphInner).unzip
        val nodes = nextInner._1.flatten
        val edges = nextInner._2.flatten
        val connecting = nexts.flatMap(e => e.headOption.map(_.id).map(displayablenode.Edge(data.id, _, None)))
        (data :: nodes ::: tailNodes, connecting ::: edges ::: tailEdges)
      case Nil =>
        (List(),List())
    }

  private def createNextEdge(id: String, tail: List[CanonicalNode]): List[displayablenode.Edge] = {
    tail.headOption.map(n => displayablenode.Edge(id, n.id, None)).toList
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
    val metaData = MetaData(process.id, process.properties.parallelism, process.properties.splitStateToDisk, process.properties.additionalFields)
    CanonicalProcess(metaData, process.properties.exceptionHandler, nodes)
  }

  private def findRootNodes(process: DisplayableProcess): List[NodeData] =
    process.nodes.filterNot(n => process.edges.exists(_.to == n.id))

  private def unFlattenNode(nodesMap: Map[String, NodeData])
                           (n: NodeData, edgesFromMap: Map[String, List[displayablenode.Edge]]): List[canonicalnode.CanonicalNode] = {
    def unflattenEdgeEnd(id: String, e: displayablenode.Edge): List[canonicalnode.CanonicalNode] = {
      unFlattenNode(nodesMap)(nodesMap(e.to), edgesFromMap.updated(id, edgesFromMap(id).filterNot(_ == e)))
    }

    def getEdges(id: String) = edgesFromMap.getOrElse(id, List())

    val handleNestedNodes: PartialFunction[NodeData, List[canonicalnode.CanonicalNode]] = {
      case data: Filter =>
        //FIXME: tutaj zakladamy ze pierwszy edge bedzie true, a drugi false - to troche slabe...
        val filterEdges = getEdges(data.id)
        val next = filterEdges.headOption.map(truePath => unflattenEdgeEnd(data.id, truePath)).getOrElse(List())
        val nextFalse = filterEdges.drop(1).lastOption.map(nf => unflattenEdgeEnd(data.id, nf)).toList.flatten
        canonicalnode.FilterNode(data, nextFalse) :: next
      case data: Switch =>
        val nexts = getEdges(data.id).collect { case e@displayablenode.Edge(_, _, Some(edgeExpr)) =>
          canonicalnode.Case(edgeExpr, unflattenEdgeEnd(data.id, e))
        }
        val default = getEdges(data.id).find(_.label.isEmpty).map { e =>
          unflattenEdgeEnd(data.id, e)
        }.toList.flatten
        canonicalnode.SwitchNode(data, nexts, default) :: Nil
      case data: Split =>
        val nexts = getEdges(data.id).map(unflattenEdgeEnd(data.id, _))
        canonicalnode.SplitNode(data, nexts) :: Nil
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