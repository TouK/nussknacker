package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

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
import pl.touk.nussknacker.engine.api.{NodeId, ValueWithContext}
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext
import pl.touk.nussknacker.engine.flink.util.keyed.KeyEnricher
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.ExtendedWindowOperator.{stateDescriptorName, Input}
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.transformers.AggregatorTypeInformations
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.triggers.FireOnEachEvent
import pl.touk.nussknacker.engine.util.KeyedValue

import java.lang

object ExtendedWindowOperator {
  // TODO_PAWEL is it ok, previously StringKeyedValue
  type Input[A] = ValueWithContext[KeyedValue[AnyRef, A]]

  // WindowOperatorBuilder.WINDOW_STATE_NAME - should be the same for compatibility
  val stateDescriptorName = "window-contents"

  implicit class OnEventOperatorKeyedStream[A](stream: KeyedStream[Input[A], AnyRef])(
      implicit fctx: FlinkCustomNodeContext
  ) {

    def extendedEventTriggerWindow(
        assigner: WindowAssigner[_ >: Input[A], TimeWindow],
        types: AggregatorTypeInformations,
        aggregateFunction: AggregateFunction[Input[A], AnyRef, AnyRef],
        trigger: Trigger[_ >: Input[A], TimeWindow]
    ): SingleOutputStreamOperator[ValueWithContext[AnyRef]] = extendedWindow(
      assigner,
      types,
      aggregateFunction,
      FireOnEachEvent(trigger),
      preserveContext = true
    )

    def extendedWindow(
        assigner: WindowAssigner[_ >: Input[A], TimeWindow],
        types: AggregatorTypeInformations,
        aggregateFunction: AggregateFunction[Input[A], AnyRef, AnyRef],
        trigger: Trigger[_ >: Input[A], TimeWindow],
        preserveContext: Boolean,
    ): SingleOutputStreamOperator[ValueWithContext[AnyRef]] = stream.transform(
      assigner.getClass.getSimpleName,
      types.returnedValueTypeInfo,
      new ExtendedWindowOperator(stream, fctx, assigner, types, aggregateFunction, trigger, preserveContext)
    )

  }

}

private[aggregate] case class NuWindowContextHolder(var nuWindowContext: NuWindowContext)

private[aggregate] sealed trait NuWindowContext

private[aggregate] final case class OnElementWindowContext(
    contextToPreserve: Option[pl.touk.nussknacker.engine.api.Context],
    timestampToOverride: Long
) extends NuWindowContext

private[aggregate] case object OnTimerWindowContext extends NuWindowContext

private[aggregate] class ExtendedWindowOperator[A](
    stream: KeyedStream[Input[A], AnyRef],
    fctx: FlinkCustomNodeContext,
    assigner: WindowAssigner[_ >: Input[A], TimeWindow],
    types: AggregatorTypeInformations,
    aggregateFunction: AggregateFunction[Input[A], AnyRef, AnyRef],
    trigger: Trigger[_ >: Input[A], TimeWindow],
    preserveContext: Boolean,
    private val contextHolderRef: NuWindowContextHolder = NuWindowContextHolder(OnTimerWindowContext)
) extends WindowOperator[AnyRef, Input[A], AnyRef, ValueWithContext[AnyRef], TimeWindow](
      assigner,
      assigner.getWindowSerializer(stream.getExecutionConfig),
      stream.getKeySelector,
      stream.getKeyType.createSerializer(stream.getExecutionConfig.getSerializerConfig),
      new AggregatingStateDescriptor(
        stateDescriptorName,
        aggregateFunction,
        types.storedTypeInfo.createSerializer(stream.getExecutionConfig.getSerializerConfig)
      ),
      new InternalSingleValueProcessWindowFunction(
        new ValueEmittingWindowFunction(fctx.convertToEngineRuntimeContext, fctx.nodeId, contextHolderRef)
      ),
      trigger,
      0L,  // lateness,
      null // tag
    ) {

  override def processElement(element: StreamRecord[ValueWithContext[KeyedValue[AnyRef, A]]]): Unit = {
    contextHolderRef.nuWindowContext =
      OnElementWindowContext(if (preserveContext) Some(element.getValue.context) else None, element.getTimestamp)
    try {
      super.processElement(element)
    } finally {
      contextHolderRef.nuWindowContext = OnTimerWindowContext
    }
  }

}

private class ValueEmittingWindowFunction(
    convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext,
    nodeId: NodeId,
    private val contextHolderRef: NuWindowContextHolder
) extends ProcessWindowFunction[AnyRef, ValueWithContext[AnyRef], AnyRef, TimeWindow] {

  @transient
  private var contextIdGenerator: ContextIdGenerator = _

  override def open(openContext: OpenContext): Unit = {
    contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId)
  }

  override def process(
      key: AnyRef,
      context: ProcessWindowFunction[AnyRef, ValueWithContext[AnyRef], AnyRef, TimeWindow]#Context,
      elements: lang.Iterable[AnyRef],
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    elements.forEach { element =>
      val contextOpt = contextHolderRef.nuWindowContext match {
        case OnElementWindowContext(contextToPreserve, timestampToOverride) =>
          // this means, that end of this window pane was triggered by an element

          // in current flink implementation out is always of type TimestampedCollector
          out.asInstanceOf[TimestampedCollector[_]].setAbsoluteTimestamp(timestampToOverride)
          contextToPreserve

        case OnTimerWindowContext =>
          // this means, that end of this window pane was triggered by a timer
          None
      }

      out.collect(
        ValueWithContext(
          element,
          KeyEnricher.enrichWithKey(
            contextOpt.getOrElse(api.Context(contextIdGenerator.nextContextId())),
            key
          )
        )
      )
    }
  }

}
