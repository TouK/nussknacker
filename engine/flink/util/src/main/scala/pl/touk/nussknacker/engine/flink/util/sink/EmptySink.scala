package pl.touk.nussknacker.engine.flink.util.sink

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api.{DisplayJson, Displayable}
import pl.touk.nussknacker.engine.flink.api.process.{BasicFlinkSink, FlinkSink}

case object EmptySink extends BasicFlinkSink {

  override def testDataOutput: Option[(Any) => String] = Option {
    case null => "null"
    case a: DisplayJson => a.asJson.spaces2
    case b => b.toString
  }

  override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
    override def invoke(value: Any): Unit = ()
  }
}

