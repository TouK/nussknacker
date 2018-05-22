package pl.touk.nussknacker.engine.example.custom

import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.functions.{KeyedProcessFunction, ProcessFunction}
import org.apache.flink.streaming.api.scala.DataStream
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.util.MultiMap
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation
import pl.touk.nussknacker.engine.flink.api.state.TimestampedEvictableStateFunction

import scala.concurrent.duration.Duration

/** Counts passing events for configurable key (i.e. for every client) in given time window */
class EventsCounter() extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[EventCount])
  def execute(@ParamName("key") key: LazyInterpreter[String],
              @ParamName("length") length: String): FlinkCustomStreamTransformation = {
    FlinkCustomStreamTransformation((start: DataStream[InterpretationResult]) => {
      val lengthInMillis = Duration(length).toMillis
      start.keyBy(key.syncInterpretationFunction)
        .process(new CounterFunction(lengthInMillis))
    })
  }
}

class CounterFunction(lengthInMillis: Long) extends TimestampedEvictableStateFunction[InterpretationResult, ValueWithContext[Any], Int] {

  override def stateDescriptor =
    new ValueStateDescriptor[MultiMap[Long, Int]]("state", classOf[MultiMap[Long, Int]])


  override def processElement(ir: InterpretationResult, ctx: KeyedProcessFunction[String, InterpretationResult, ValueWithContext[Any]]#Context,
                              out: Collector[ValueWithContext[Any]]): Unit = {

    moveEvictionTime(lengthInMillis, ctx)

    val eventCount = stateValue.add(ctx.timestamp(), 1)
    state.update(eventCount)

    val eventsCount = eventCount.map.values.flatten.sum
    out.collect(
      ValueWithContext(EventCount(count = eventsCount), ir.finalContext)
    )
  }

}

case class EventCount(count: Long)
