package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.InterpretationResult
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport

/**
 * Implementations of this trait can use LazyParameters and e.g. ignore output (requiresOutput = false in SinkFactory)
 */
trait FlinkSink extends Sink {

  def registerSink(dataStream: DataStream[InterpretationResult],
                   flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_]

}

/**
 * This is basic Flink sink, which just uses *output* expression from sink definition
 */
trait BasicFlinkSink extends FlinkSink with ExplicitUidInOperatorsSupport {

  override def registerSink(dataStream: DataStream[InterpretationResult],
                            flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] = {
    setUidToNodeIdIfNeed(flinkNodeContext, dataStream.map(_.output).addSink(toFlinkFunction))
  }

  def toFlinkFunction: SinkFunction[Any]

}
