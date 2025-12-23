package pl.touk.nussknacker.engine.process.helpers

import org.apache.flink.api.connector.sink2.{Sink, SinkWriter, WriterInitContext}
import pl.touk.nussknacker.engine.api.process.SinkFactory
import pl.touk.nussknacker.engine.flink.util.sink.SingleValueSinkFactory

object SinkForType {

  def apply[T <: AnyRef](resultsHolder: => TestResultsHolder[T]): SinkFactory = new SingleValueSinkFactory(
    new FlinkSinkForType(resultsHolder)
  )

}

class FlinkSinkForType[T <: AnyRef](resultsHolder: => TestResultsHolder[T]) extends Sink[T] {

  override def createWriter(context: WriterInitContext): SinkWriter[T] = new SinkWriter[T] {
    override def write(element: T, context: SinkWriter.Context): Unit = resultsHolder.add(element)

    override def flush(endOfInput: Boolean): Unit = {}

    override def close(): Unit = {}
  }

}
