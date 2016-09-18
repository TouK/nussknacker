package pl.touk.esp.engine.compiledgraph

import pl.touk.esp.engine._
import pl.touk.esp.engine.api.FoldingFunction
import pl.touk.esp.engine.definition.CustomNodeInvoker
import pl.touk.esp.engine.splittedgraph.end.{End, NormalEnd}
import pl.touk.esp.engine.splittedgraph.splittednode
import pl.touk.esp.engine.splittedgraph.splittednode.CustomNode

object part {

  sealed trait ProcessPart {
    def id: String
    def ends: List[End]
  }

  case class SourcePart(id: String, obj: api.process.Source[Any], source: splittednode.Source,
                        nextParts: List[SubsequentPart], ends: List[End]) extends ProcessPart

  sealed trait SubsequentPart extends ProcessPart

  case class AggregatePart(id: String, durationInMillis: Long, slideInMillis: Long,
                           aggregatedVar: String, foldingFun: Option[FoldingFunction[Any]], aggregate: splittednode.Aggregate,
                           nextParts: List[SubsequentPart], ends: List[End]) extends SubsequentPart

  case class CustomNodePart(id: String,
                            outputVar: String,
                            customNodeInvoker: CustomNodeInvoker[_],
                            customNode: CustomNode, nextParts: List[SubsequentPart], ends: List[End]) extends SubsequentPart

  case class SinkPart(id: String, obj: api.process.Sink, sink: splittednode.Sink) extends SubsequentPart {
    override lazy val ends = List(NormalEnd(sink.id))
  }

}
