package pl.touk.esp.ui.process.marshall

import pl.touk.esp.engine.canonicalgraph.canonicalnode._
import pl.touk.esp.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, displayablenode}

object ProcessConverter {

  def toDisplayable(process: CanonicalProcess): DisplayableProcess = {
    val ne = toGraphInner(process.nodes)
    val (n, e) = ne
    DisplayableProcess(process.metaData, n, e)
  }

  private def toGraphInner(nodes: List[canonicalnode.CanonicalNode]): (List[displayablenode.DisplayableNode], List[displayablenode.Edge]) =
    nodes match {
      case canonicalnode.Source(id, ref) :: tail =>
        val (tailNodes, tailEdges) = toGraphInner(tail)
        (displayablenode.Source(id, ref) :: tailNodes, createNextEdge(id, tail, None) ::: tailEdges)
      case canonicalnode.Sink(id, ref, endResult) :: Nil =>
        (List(displayablenode.Sink(id, ref, endResult)), List())
      case canonicalnode.Sink(id, ref, endResult) :: tail =>
        throw new IllegalArgumentException(s"Unexpected tail: $tail after sink")
      case canonicalnode.VariableBuilder(id, varName, fields) :: tail =>
        val (tailNodes, tailEdges) = toGraphInner(tail)
        (displayablenode.VariableBuilder(id, varName, fields) :: tailNodes, createNextEdge(id, tail, None) ::: tailEdges)
      case canonicalnode.Processor(id, service) :: tail =>
        val (tailNodes, tailEdges) = toGraphInner(tail)
        (displayablenode.Processor(id, service) :: tailNodes, createNextEdge(id, tail, None) ::: tailEdges)
      case canonicalnode.Enricher(id, service, output) :: tail =>
        val (tailNodes, tailEdges) = toGraphInner(tail)
        (displayablenode.Enricher(id, service, output) :: tailNodes, createNextEdge(id, tail, None) ::: tailEdges)
      case canonicalnode.Filter(id, expr, nextFalse) :: tail =>
        val (nextFalseNodes, nextFalseEdges) = toGraphInner(nextFalse)
        val nextFalseEdgesConnectedToFilter = nextFalseNodes match {
          case Nil => nextFalseEdges
          case h :: _ => displayablenode.Edge(id, h.id, None) :: nextFalseEdges
        }
        val (tailNodes, tailEdges) = toGraphInner(tail)
        (displayablenode.Filter(id, expr) :: nextFalseNodes ::: tailNodes, createNextEdge(id, tail, Some(expr)) ::: nextFalseEdgesConnectedToFilter ::: tailEdges)
      case canonicalnode.Switch(id, expr, exprVal, nexts, defaultNext) :: tail =>
        val (defaultNextNodes, defaultNextEdges) = toGraphInner(defaultNext)
        val defaultNextEdgesConnectedToSwitch = defaultNextNodes match {
          case Nil => defaultNextEdges
          case h :: _ => displayablenode.Edge(id, h.id, None) :: defaultNextEdges
        }
        val (tailNodes, tailEdges) = toGraphInner(tail)
        val (nextNodes, nextEdges) = unzipListTuple(nexts.map { c =>
          val (nextNodeNodes, nextNodeEdges) = toGraphInner(c.nodes)
          (nextNodeNodes, nextNodeNodes.headOption.map(n => displayablenode.Edge(id, n.id, Some(c.expression))).toList ::: nextNodeEdges)
        })
        (displayablenode.Switch(id, expr, exprVal) :: defaultNextNodes ::: nextNodes ::: tailNodes, createNextEdge(id, tail, None) ::: defaultNextEdgesConnectedToSwitch ::: nextEdges ::: tailEdges)
      case canonicalnode.Aggregate(id, aggregatedVar, keyExpr, duration, slide, triggerExpr, foldingFun)::tail =>
        val (tailNodes, tailEdges) = toGraphInner(tail)
        (displayablenode.Aggregate(id, aggregatedVar, keyExpr, duration, slide, triggerExpr, foldingFun) :: tailNodes, createNextEdge(id, tail, None) ::: tailEdges)
      case Nil =>
        (List(),List())
    }

  private def createNextEdge(id: String, tail: List[CanonicalNode], label: Option[Expression]): List[displayablenode.Edge] = {
    tail.headOption.map(n => displayablenode.Edge(id, n.id, label)).toList
  }

  private def unzipListTuple[A, B](a: List[(List[A], List[B])]): (List[A], List[B]) = {
    val (aList, bList) = a.unzip
    (aList.flatten, bList.flatten)
  }

  def fromDisplayable(process: DisplayableProcess): CanonicalProcess = {
    val nodesMap = process.nodes.groupBy(_.id).mapValues(_.head)
    val edgesFromMapStart = process.edges.groupBy(_.from)

    def unFlattenNode(n: displayablenode.DisplayableNode, edgesFromMap: Map[String, List[displayablenode.Edge]]): List[canonicalnode.CanonicalNode] = {
      def unflattenEdgeEnd(id: String, e: displayablenode.Edge): List[canonicalnode.CanonicalNode] = {
        unFlattenNode(nodesMap(e.to), edgesFromMap.updated(id, edgesFromMap(id).filterNot(_ == e)))
      }
      n match {
        case displayablenode.Source(id, ref) =>
          val firstEdge = edgesFromMap(id).head
          canonicalnode.Source(id, ref) :: unflattenEdgeEnd(id, firstEdge)
        case displayablenode.VariableBuilder(id, varName, fields) =>
          val firstEdge = edgesFromMap(id).head
          canonicalnode.VariableBuilder(id, varName, fields) :: unflattenEdgeEnd(id, firstEdge)
        case displayablenode.Processor(id, service) =>
          val firstEdge = edgesFromMap(id).head
          canonicalnode.Processor(id, service) :: unflattenEdgeEnd(id, firstEdge)
        case displayablenode.Enricher(id, service, output) =>
          val firstEdge = edgesFromMap(id).head
          canonicalnode.Enricher(id, service, output) :: unflattenEdgeEnd(id, firstEdge)
        case displayablenode.Filter(id, expr) =>
          val filterEdges = edgesFromMap(id)
          val next = unflattenEdgeEnd(id, filterEdges.head)
          val nextFalse = filterEdges.tail.lastOption.map(nf => unflattenEdgeEnd(id, nf)).toList.flatten
          canonicalnode.Filter(id, expr, nextFalse) :: next
        case displayablenode.Switch(id, expr, exprVal) =>
          val nexts = edgesFromMap(id).collect { case e@displayablenode.Edge(_, _, Some(edgeExpr)) =>
            canonicalnode.Case(edgeExpr, unflattenEdgeEnd(id, e))
          }
          val default = edgesFromMap(id).find(_.label.isEmpty).map { e =>
            unflattenEdgeEnd(id, e)
          }.toList.flatten
          canonicalnode.Switch(id, expr, exprVal, nexts, default) :: Nil
        case displayablenode.Aggregate(id, aggregatedVar, keyExpr, duration, slide, triggerExpr, foldingFun) =>
          canonicalnode.Aggregate(id, aggregatedVar, keyExpr, duration, slide, triggerExpr, foldingFun) :: Nil
        case displayablenode.Sink(id, ref, endResult) =>
          canonicalnode.Sink(id, ref, endResult) :: Nil
      }
    }
    val nodes = unFlattenNode(process.nodes.head, edgesFromMapStart)
    CanonicalProcess(process.metaData, nodes)
  }

}