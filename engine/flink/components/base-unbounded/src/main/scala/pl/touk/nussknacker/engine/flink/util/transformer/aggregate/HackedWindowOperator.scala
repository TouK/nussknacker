package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.functions.{AggregateFunction, OpenContext, RuntimeContext}
import org.apache.flink.api.common.state.AggregatingStateDescriptor
import org.apache.flink.streaming.api.datastream.{KeyedStream, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.operators.TimestampedCollector
import org.apache.flink.streaming.api.windowing.assigners.WindowAssigner
import org.apache.flink.streaming.api.windowing.triggers.Trigger
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.runtime.operators.windowing.WindowOperator
import org.apache.flink.streaming.runtime.operators.windowing.functions.InternalSingleValueProcessWindowFunction
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api.ValueWithContext
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext
import pl.touk.nussknacker.engine.flink.util.keyed.{KeyEnricher, StringKeyedValue}
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.HackedWindowOperator.{Input, elementHolder, stateDescriptorName, timestampToOverrideHolder}
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.transformers.AggregatorTypeInformations
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.triggers.FireOnEachEvent

import java.lang

object HackedWindowOperator {
  type Input[A] = ValueWithContext[StringKeyedValue[A]]

  // We use ThreadLocal to pass context from WindowOperator.processElement to ProcessWindowFunction
  // without modifying too much Flink code. This assumes that window is triggered only on event
  val elementHolder = new ThreadLocal[api.Context]
  // TODO_PAWEL make this and other holder private here, and pass lambda to set it to trigger, add callback to trigger in other words. Yes, it is possible
  val timestampToOverrideHolder = new ThreadLocal[Long]

  // WindowOperatorBuilder.WINDOW_STATE_NAME - should be the same for compatibility
  val stateDescriptorName = "window-contents"

  implicit class OnEventOperatorKeyedStream[A](stream: KeyedStream[Input[A], String])(
      implicit fctx: FlinkCustomNodeContext
  ) {

    def hackedEventTriggerWindow(
        assigner: WindowAssigner[_ >: Input[A], TimeWindow],
        types: AggregatorTypeInformations,
        aggregateFunction: AggregateFunction[Input[A], AnyRef, AnyRef],
        trigger: Trigger[_ >: Input[A], TimeWindow]
    ): SingleOutputStreamOperator[ValueWithContext[AnyRef]] = hackedWindow(assigner, types, aggregateFunction, FireOnEachEvent[ValueWithContext[StringKeyedValue[A]], TimeWindow](trigger))

    def hackedWindow(
                            assigner: WindowAssigner[_ >: Input[A], TimeWindow],
                            types: AggregatorTypeInformations,
                            aggregateFunction: AggregateFunction[Input[A], AnyRef, AnyRef],
                            trigger: Trigger[_ >: Input[A], TimeWindow]
                          ): SingleOutputStreamOperator[ValueWithContext[AnyRef]] = stream.transform(
      assigner.getClass.getSimpleName,
      types.returnedValueTypeInfo,
      new HackedWindowOperator(stream, fctx, assigner, types, aggregateFunction, trigger)
    )

  }

}

@silent("deprecated")
class HackedWindowOperator[A](
    stream: KeyedStream[Input[A], String],
    fctx: FlinkCustomNodeContext,
    assigner: WindowAssigner[_ >: Input[A], TimeWindow],
    types: AggregatorTypeInformations,
    aggregateFunction: AggregateFunction[Input[A], AnyRef, AnyRef],
    trigger: Trigger[_ >: Input[A], TimeWindow]
) extends WindowOperator[String, Input[A], AnyRef, ValueWithContext[AnyRef], TimeWindow](
      assigner,
      assigner.getWindowSerializer(stream.getExecutionConfig),
      stream.getKeySelector,
      stream.getKeyType.createSerializer(stream.getExecutionConfig),
      new AggregatingStateDescriptor(
        stateDescriptorName,
        aggregateFunction,
        types.storedTypeInfo.createSerializer(stream.getExecutionConfig)
      ),
      new InternalSingleValueProcessWindowFunction(
        new ValueEmittingWindowFunction(fctx.convertToEngineRuntimeContext, fctx.nodeId)
      ),
      trigger,
      0L,  // lateness,
      null // tag
    ) {

  override def processElement(element: StreamRecord[ValueWithContext[StringKeyedValue[A]]]): Unit = {
    elementHolder.set(element.getValue.context)
    try {
      super.processElement(element)
    } finally {
      elementHolder.remove()
      timestampToOverrideHolder.remove()
    }
  }

}

private class ValueEmittingWindowFunction(
    convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext,
    nodeId: String
) extends ProcessWindowFunction[AnyRef, ValueWithContext[AnyRef], String, TimeWindow] {

  @transient
  private var contextIdGenerator: ContextIdGenerator = _

  override def open(openContext: OpenContext): Unit = {
    contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId)
  }

  override def process(
      key: String,
      context: ProcessWindowFunction[AnyRef, ValueWithContext[AnyRef], String, TimeWindow]#Context,
      elements: lang.Iterable[AnyRef],
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    // TODO_PAWEL tu mozna by ten out scastowac na timestampedcollector i mu ustawic timestamp, tylko problem jest taki ze nie wiadomo jaki
    // tutaj niby widze, ze te elements maja typ StreamRecord, mozna z nich wiec wziac timestamp
    // wiec moge ustawic np timestamp ostatniego z nich, ale nie zawsze. czasem jest juz ustawiony prawidlowy w tym 'out' timestampcollector
    // tylko jak mam odroznic sytuacje gdy window jest jakby przerwany wczesniej, w tym triggerze. mam jakis stan trzymac?
    elements.forEach { element =>
      val ctx = Option(elementHolder.get()).getOrElse(api.Context(contextIdGenerator.nextContextId()))

      out match {
        case timedOut: TimestampedCollector[_] =>
          Option(timestampToOverrideHolder.get()).foreach(timestamp => timedOut.setAbsoluteTimestamp(timestamp))
        // TODO_PAWEL maybe we should throw in other case?
        case _ =>
      }

      out.collect(ValueWithContext(element, KeyEnricher.enrichWithKey(ctx, key)))
    }
  }

}
