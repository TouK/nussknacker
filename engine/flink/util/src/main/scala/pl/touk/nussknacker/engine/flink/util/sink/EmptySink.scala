package pl.touk.nussknacker.engine.flink.util.sink

import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.functions.sink.DiscardingSink
import org.apache.flink.streaming.api.scala.DataStream
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}

case object EmptySink extends FlinkSink {

  override def registerSink(dataStream: DataStream[Context], flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] =
    dataStream.addSink(new DiscardingSink[Context]())

}

