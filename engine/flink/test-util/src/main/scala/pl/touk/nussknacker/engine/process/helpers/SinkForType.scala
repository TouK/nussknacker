package pl.touk.nussknacker.engine.process.helpers

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction.Context
import pl.touk.nussknacker.engine.flink.api.process.BasicFlinkSink
import pl.touk.nussknacker.test.WithDataList

//this has to be overridden by case object to work properly in tests
trait SinkForType[T] extends BasicFlinkSink with WithDataList[T] with Serializable {

  override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {

    override def invoke(value: Any, context: Context): Unit = {
      add(value.asInstanceOf[T])
    }
  }

  override def testDataOutput: Option[Any => String] = Some(_.toString)

}