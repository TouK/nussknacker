package pl.touk.nussknacker.engine.flink.util.sink

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api.Displayable
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink

case object EmptySink extends FlinkSink {

  override def testDataOutput: Option[(Any) => String] = Option {
    case a: Displayable => a.display.spaces2
    case b => b.toString
  }

  override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
    override def invoke(value: Any): Unit = ()
  }
}

