package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.InterpretationResult
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.process.Sink

/**
 * Implementations of this trait can use LazyParameters and e.g. ignore output (requiresOutput = false in SinkFactory)
 */
trait FlinkSink extends Sink {

  def registerSink(dataStream: DataStream[InterpretationResult],
                   sinkContext: FlinkSinkContext): DataStreamSink[_]

}

case class FlinkSinkContext(lazyParameterFunctionHelper: FlinkLazyParameterFunctionHelper, validationContext: ValidationContext)

/**
 * This is basic Flink sink, which just uses *output* expression from sink definition
 */
trait BasicFlinkSink extends FlinkSink {

  override def registerSink(dataStream: DataStream[InterpretationResult],
                            sinkContext: FlinkSinkContext): DataStreamSink[_] = {
    dataStream.map(_.output).addSink(toFlinkFunction)
  }

  def toFlinkFunction: SinkFunction[Any]

}
