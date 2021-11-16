package pl.touk.nussknacker.engine.process.helpers

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api.process.SinkFactory
import pl.touk.nussknacker.engine.flink.util.sink.SingleValueSinkFactory
import pl.touk.nussknacker.engine.process.helpers.SinkForType.SinkForTypeFunction
import pl.touk.nussknacker.test.WithDataList


trait SinkForType[T <: AnyRef] extends WithDataList[T] with Serializable {

  def toSinkFactory: SinkFactory = new SingleValueSinkFactory(toSinkFunction)

  def toSinkFunction: SinkFunction[T] = new SinkForTypeFunction(this)

}

object SinkForType {

  private class SinkForTypeFunction[T <: AnyRef](sft: SinkForType[T]) extends SinkFunction[T] {
    override def invoke(value: T, context: SinkFunction.Context): Unit = {
      sft.add(value)
    }
  }

}