package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.common.functions.{AbstractRichFunction, MapFunction}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api.{DeadEndReference, EndReference, InterpretationResult, PartReference}
import pl.touk.nussknacker.engine.compiledgraph.part.TypedEnd
import pl.touk.nussknacker.engine.flink.util.metrics.{InstantRateMeterWithCount, MetricUtils}
import pl.touk.nussknacker.engine.splittedgraph.end.{BranchEnd, DeadEnd, End, NormalEnd}
import pl.touk.nussknacker.engine.util.metrics.RateMeter

private[registrar] class EndRateMeterFunction(ends: Seq[TypedEnd]) extends AbstractRichFunction
  with MapFunction[InterpretationResult, InterpretationResult] with SinkFunction[InterpretationResult] {

  @transient private var meterByReference: Map[PartReference, RateMeter] = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)

    val parentGroupForNormalEnds = "end"
    val parentGroupForDeadEnds = "dead_end"

    def registerRateMeter(end: End): InstantRateMeterWithCount = {
      val baseGroup = end match {
        case _: NormalEnd => parentGroupForNormalEnds
        case _: DeadEnd => parentGroupForDeadEnds
        case _: BranchEnd => parentGroupForDeadEnds
      }
      InstantRateMeterWithCount.register(Map("nodeId" -> end.nodeId), List(baseGroup), new MetricUtils(getRuntimeContext))
    }

    meterByReference = ends.map(_.end).map { end =>
      val reference = end match {
        case NormalEnd(nodeId) => EndReference(nodeId)
        case DeadEnd(nodeId) => DeadEndReference(nodeId)
        case BranchEnd(definition) => definition.joinReference
      }
      reference -> registerRateMeter(end)
    }.toMap[PartReference, RateMeter]
  }

  override def map(value: InterpretationResult): InterpretationResult = {
    val meter = meterByReference.getOrElse(value.reference, throw new IllegalArgumentException("Unexpected reference: " + value.reference))
    meter.mark()
    value
  }

  override def invoke(value: InterpretationResult, context: SinkFunction.Context): Unit = {
    map(value)
  }
}
