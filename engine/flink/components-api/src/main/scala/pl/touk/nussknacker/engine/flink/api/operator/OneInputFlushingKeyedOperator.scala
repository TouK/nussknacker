package pl.touk.nussknacker.engine.flink.api.operator

import org.apache.flink.api.common.state.{State, StateDescriptor}
import org.apache.flink.runtime.state.{VoidNamespace, VoidNamespaceSerializer}
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.operators.{BoundedOneInput, KeyedProcessOperator, Output}
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord

/**
 * A [[KeyedProcessFunction]] that flushes per-key entries from keyed state when the bounded input ends.
 * Implemented together with [[OneInputFlushingKeyedOperator]], which drives the flush across all keys.
 */
trait KeyedFlushFunction[K, OUT] { this: KeyedProcessFunction[K, _, OUT] =>

  /**
   * Keyed-state descriptor used ONLY to enumerate the keys to flush: the operator iterates the keys that have an entry
   * in this state and calls [[flushForCurrentKey]] for each. That state's values are never read by the
   * operator, so the callback is free to touch any keyed state for the current key.
   *
   * Contract: the returned descriptor must cover EVERY key that may hold pending entries.
   * With a single state, return that state. With multiple states whose key sets diverge (neither a superset
   * of the other), return a single descriptor of a dedicated index state that stores some value for all existing keys.
   */
  def flushStateDescriptor: StateDescriptor[_, _]

  /**
   * Flushes the current key's pending entries to `output`. Called once per key when the bounded input ends, with the
   * key context already set.
   */
  def flushForCurrentKey(key: K, output: Output[StreamRecord[OUT]]): Unit

}

/**
  * A [[KeyedProcessOperator]] that, when the bounded input ends, flushes every key's pending entries by calling the
  * [[function]]'s `flushForCurrentKey`. [[endInput()]] runs without a key context, so it uses `applyToAllKeys` to set the
  * per-key context before each call.
  */
class OneInputFlushingKeyedOperator[K, IN, OUT](
    function: KeyedProcessFunction[K, IN, OUT] with KeyedFlushFunction[K, OUT]
) extends KeyedProcessOperator[K, IN, OUT](function)
    with BoundedOneInput
    with OneInputFlushingKeyedOperatorMixin[K, OUT] {

  override def endInput(): Unit =
    flushForAllKeys(function, output)

}

private[operator] trait OneInputFlushingKeyedOperatorMixin[K, OUT] { self: KeyedProcessOperator[K, _, OUT] =>

  protected def flushForAllKeys(function: KeyedFlushFunction[K, OUT], output: Output[StreamRecord[OUT]]): Unit = {
    getKeyedStateBackend[K].applyToAllKeys(
      VoidNamespace.INSTANCE,
      VoidNamespaceSerializer.INSTANCE,
      function.flushStateDescriptor.asInstanceOf[StateDescriptor[State, _]],
      (key, _: State) => function.flushForCurrentKey(key, output)
    )
  }

}
