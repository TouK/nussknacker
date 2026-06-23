package pl.touk.nussknacker.engine.flink.api.operator

import org.apache.flink.api.common.state.{State, StateDescriptor}
import org.apache.flink.runtime.state.{KeyedStateFunction, VoidNamespace, VoidNamespaceSerializer}
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.operators.{BoundedOneInput, KeyedProcessOperator}

/**
  * A `KeyedProcessOperator` that, when the bounded input ends, flushes the function's still-queued events. Without this,
  * processing-time timers never fire after input end and queued events are lost.
  *
  * Keyed state is scoped to a single key, and `endInput()` runs without a key context, so iterating over the function's
  * state directly would only reach the last processed key. `applyToAllKeys` sets the key context for every key before
  * invoking the callback, so each key's queued events are flushed.
  */
class BoundedEndInputFlushingOperator[K, IN, OUT](
    function: KeyedProcessFunction[K, IN, OUT] with EndInputFlushableFunction[OUT]
) extends KeyedProcessOperator[K, IN, OUT](function)
    with BoundedOneInput {

  override def endInput(): Unit =
    getKeyedStateBackend[K].applyToAllKeys(
      VoidNamespace.INSTANCE,
      VoidNamespaceSerializer.INSTANCE,
      function.flushStateDescriptor.asInstanceOf[StateDescriptor[State, _]],
      new KeyedStateFunction[K, State] {
        override def process(key: K, state: State): Unit =
          function.flushPendingEventsForCurrentKey(output)
      }
    )

}
