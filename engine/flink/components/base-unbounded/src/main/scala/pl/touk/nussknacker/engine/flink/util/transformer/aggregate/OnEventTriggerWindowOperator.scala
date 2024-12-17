package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.functions.{AggregateFunction, OpenContext, RuntimeContext}
import org.apache.flink.api.common.state.AggregatingStateDescriptor
import org.apache.flink.streaming.api.datastream.{KeyedStream, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
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
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.OnEventTriggerWindowOperator.{
  Input,
  elementHolder,
  stateDescriptorName
}
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.transformers.AggregatorTypeInformations
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.triggers.FireOnEachEvent

import java.lang

object OnEventTriggerWindowOperator {
  type Input[A] = ValueWithContext[StringKeyedValue[A]]

  // We use ThreadLocal to pass context from WindowOperator.processElement to ProcessWindowFunction
  // without modifying too much Flink code. This assumes that window is triggered only on event
  val elementHolder = new ThreadLocal[api.Context]

  // WindowOperatorBuilder.WINDOW_STATE_NAME - should be the same for compatibility
  val stateDescriptorName = "window-contents"

  implicit class OnEventOperatorKeyedStream[A](stream: KeyedStream[Input[A], String])(
      implicit fctx: FlinkCustomNodeContext
  ) {

    def eventTriggerWindow(
        assigner: WindowAssigner[_ >: Input[A], TimeWindow],
        types: AggregatorTypeInformations,
        aggregateFunction: AggregateFunction[Input[A], AnyRef, AnyRef],
        trigger: Trigger[_ >: Input[A], TimeWindow]
    ): SingleOutputStreamOperator[ValueWithContext[AnyRef]] = stream.transform(
      assigner.getClass.getSimpleName,
      types.returnedValueTypeInfo,
      new OnEventTriggerWindowOperator(stream, fctx, assigner, types, aggregateFunction, trigger)
    )

  }

}

@silent("deprecated")
class OnEventTriggerWindowOperator[A](
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
      FireOnEachEvent[ValueWithContext[StringKeyedValue[A]], TimeWindow](trigger),
      0L,  // lateness,
      null // tag
    ) {

  override def processElement(element: StreamRecord[ValueWithContext[StringKeyedValue[A]]]): Unit = {
    elementHolder.set(element.getValue.context)
    try {
      super.processElement(element)
    } finally {
      elementHolder.remove()
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
    elements.forEach { element =>
      val ctx = Option(elementHolder.get()).getOrElse(api.Context(contextIdGenerator.nextContextId()))
      out.collect(ValueWithContext(element, KeyEnricher.enrichWithKey(ctx, key)))
    }
  }

}
