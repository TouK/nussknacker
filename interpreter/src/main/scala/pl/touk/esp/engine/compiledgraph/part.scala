package pl.touk.esp.engine.compiledgraph

import pl.touk.esp.engine._
import pl.touk.esp.engine.api.FoldingFunction
import pl.touk.esp.engine.definition.CustomNodeInvoker
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.splittedgraph.end.{End, NormalEnd}
import pl.touk.esp.engine.splittedgraph.splittednode
import pl.touk.esp.engine.splittedgraph.splittednode.SplittedNode

object part {

  sealed trait ProcessPart {
    type T <: NodeData
    def node: SplittedNode[T]
    def id: String = node.id
    def ends: List[End]
  }

  case class SourcePart(obj: api.process.Source[Any], node: splittednode.SourceNode,
                        nextParts: List[SubsequentPart], ends: List[End]) extends ProcessPart {
    override type T = Source
  }

  sealed trait SubsequentPart extends ProcessPart

  case class AggregatePart(foldingFun: Option[FoldingFunction[Any]],
                           node: splittednode.OneOutputSubsequentNode[Aggregate],
                           nextParts: List[SubsequentPart], ends: List[End]) extends SubsequentPart {
    override type T = Aggregate

    def triggerExpression: Option[Expression] = this.node.data.triggerExpression
    def durationInMillis: Long = this.node.data.durationInMillis
    def slideInMillis: Long = this.node.data.stepInMillis
    def aggregatedVar: String = this.node.data.aggregatedVar
  }

  case class CustomNodePart(customNodeInvoker: CustomNodeInvoker[_],
                            node: splittednode.OneOutputSubsequentNode[CustomNode],
                            nextParts: List[SubsequentPart], ends: List[End]) extends SubsequentPart {
    override type T = CustomNode

    def outputVar: String = node.data.outputVar
  }

  case class SinkPart(obj: api.process.Sink, node: splittednode.EndingNode[Sink]) extends SubsequentPart {
    override type T = Sink

    override lazy val ends = List(NormalEnd(node.id))
  }

}