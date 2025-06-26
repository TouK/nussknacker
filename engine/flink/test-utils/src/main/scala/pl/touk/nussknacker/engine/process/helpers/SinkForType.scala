package pl.touk.nussknacker.engine.process.helpers

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api.process.SinkFactory
import pl.touk.nussknacker.engine.flink.util.sink.SingleValueSinkFactory

object SinkForType {

  def apply[T <: AnyRef](resultsHolder: => TestResultsHolder[T]): SinkFactory = new SingleValueSinkFactory(
    new SinkForTypeFunction(resultsHolder)
  )

}

class SinkForTypeFunction[T <: AnyRef](resultsHolder: => TestResultsHolder[T]) extends SinkFunction[T] {

  override def invoke(value: T, context: SinkFunction.Context): Unit = {
    resultsHolder.add(value)
  }

}
