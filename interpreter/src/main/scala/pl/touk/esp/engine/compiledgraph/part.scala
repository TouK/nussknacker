package pl.touk.esp.engine.compiledgraph

import pl.touk.esp.engine._
import pl.touk.esp.engine.api.FoldingFunction
import pl.touk.esp.engine.splittedgraph.splittednode

object part {

  sealed trait ProcessPart {
    def id: String
  }

  case class SourcePart(id: String, obj: api.process.Source[Any], source: splittednode.Source, nextParts: List[SubsequentPart]) extends ProcessPart

  sealed trait SubsequentPart extends ProcessPart

  case class AggregatePart(id: String, durationInMillis: Long, slideInMillis: Long,
                           aggregatedVar: String, foldingFun: Option[FoldingFunction[Any]], aggregate: splittednode.Aggregate,
                           nextParts: List[SubsequentPart]) extends SubsequentPart

  case class SinkPart(id: String, obj: api.process.Sink, sink: splittednode.Sink) extends SubsequentPart


}
