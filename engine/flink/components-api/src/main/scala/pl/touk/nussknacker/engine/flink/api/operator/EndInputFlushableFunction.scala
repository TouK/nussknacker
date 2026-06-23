package pl.touk.nussknacker.engine.flink.api.operator

import org.apache.flink.api.common.state.StateDescriptor
import org.apache.flink.streaming.api.operators.Output
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord

/**
  * A KeyedProcessFunction that keeps events queued in keyed state and can flush them when the bounded input ends.
  * Implemented together with BoundedEndInputFlushingOperator, which drives the flush across all keys.
  */
trait EndInputFlushableFunction[OUT] {

  /**
    * The keyed state holding the queued events. The operator uses it to enumerate all keys.
    */
  def flushStateDescriptor: StateDescriptor[_, _]

  /**
    * Flushes the events queued for the key currently set on the keyed state backend.
    */
  def flushPendingEventsForCurrentKey(output: Output[StreamRecord[OUT]]): Unit

}
