package pl.touk.esp.engine.splittedgraph

import pl.touk.esp.engine.graph.sink.SinkRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.engine.splittedgraph.splittednode._

object part {

  sealed trait ProcessPart {
    def id: String
  }

  case class SourcePart(id: String, ref: SourceRef, source: Source, nextParts: List[SubsequentPart]) extends ProcessPart

  sealed trait SubsequentPart extends ProcessPart

  case class AggregateExpressionPart(id: String, durationInMillis: Long, slideInMillis: Long,
                                     aggregatedVar: String, aggregate: Aggregate,
                                     nextPart: AfterAggregationPart) extends SubsequentPart

  case class AfterAggregationPart(id: String, aggregatedVar: String,
                                  next: Next, nextParts: List[SubsequentPart]) extends SubsequentPart

  case class SinkPart(id: String, ref: SinkRef, sink: Sink) extends SubsequentPart

}