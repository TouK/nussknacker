package pl.touk.esp.engine.example.custom

import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.scala.DataStream
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.util.MultiMap
import pl.touk.esp.engine.flink.api.process.FlinkCustomStreamTransformation
import pl.touk.esp.engine.flink.api.state.TimestampedEvictableState

import scala.concurrent.duration.Duration

/** Counts passing events for configurable key (i.e. for every client) in given time window */
class EventsCounter() extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[EventCount])
  def execute(@ParamName("key") key: LazyInterpreter[String],
              @ParamName("length") length: String) = {
    FlinkCustomStreamTransformation((start: DataStream[InterpretationResult]) => {
      val lengthInMillis = Duration(length).toMillis
      start.keyBy(key.syncInterpretationFunction)
        .transform("eventsCounter", new CounterFunction(lengthInMillis))
    })
  }
}

class CounterFunction(lengthInMillis: Long) extends TimestampedEvictableState[Int] {

  override def stateDescriptor =
    new ValueStateDescriptor[MultiMap[Long, Int]]("state", classOf[MultiMap[Long, Int]])

  override def processElement(element: StreamRecord[InterpretationResult]): Unit = {
    setEvictionTimeForCurrentKey(element.getTimestamp + lengthInMillis)
    state.update(filterState(element.getTimestamp, lengthInMillis))

    val ir = element.getValue
    val eventCount = stateValue.add(element.getTimestamp, 1)
    state.update(eventCount)

    val eventsCount = eventCount.map.values.flatten.sum
    output.collect(new StreamRecord[ValueWithContext[Any]](
      ValueWithContext(EventCount(count = eventsCount), ir.finalContext), element.getTimestamp)
    )
  }
}

case class EventCount(count: Long)

case class PreviousTransaction(amount: Int)