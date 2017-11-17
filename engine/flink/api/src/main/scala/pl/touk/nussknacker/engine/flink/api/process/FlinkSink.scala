package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api.process.Sink

trait FlinkSink extends Sink {

  def toFlinkFunction: SinkFunction[Any]

}
