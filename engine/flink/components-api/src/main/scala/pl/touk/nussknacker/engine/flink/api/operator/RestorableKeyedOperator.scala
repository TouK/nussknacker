package pl.touk.nussknacker.engine.flink.api.operator

import org.apache.flink.api.common.state.{State, StateDescriptor}
import org.apache.flink.runtime.state.{StateInitializationContext, VoidNamespace, VoidNamespaceSerializer}
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.operators.{InternalTimerService, KeyedProcessOperator}

/**
 * Capability a function provides to take per-key action once after a state restore.
 * Independent of [[KeyedFlushFunction]]: a function may be restorable, flushable, both, or neither.
 */
trait KeyedRestoreFunction[K, OUT] { this: KeyedProcessFunction[K, _, OUT] =>

  /**
   * Keyed-state descriptor used ONLY to enumerate the keys to restore: the operator iterates the keys that have an entry
   * in this state and calls [[restoreForCurrentKey]] for each. That state's values are never read by the
   * operator, so the callback is free to touch any keyed state for the current key.
   *
   * Contract: the returned descriptor must cover EVERY key that needs restoring.
   * With a single state, return that state. With multiple states whose key sets diverge (neither a superset
   * of the other), return a descriptor of a dedicated index state stores some value for all existing keys.
   */
  def restoreStateDescriptor: StateDescriptor[_, _]

  /**
   * Called once per key after a restore, with the key context already set. Use `timerService` to re-register any timers
   * the key needs. Implementations that need no rescheduling (e.g. event time) should make this a no-op.
   */
  def restoreForCurrentKey(key: K, timerService: InternalTimerService[VoidNamespace]): Unit

}

/**
 * A [[KeyedProcessOperator]] that, after a restore, runs the [[function]]'s `restoreForCurrentKey` for every key.
 * The restore-only counterpart of [[RestorableOneInputFlushingKeyedOperator]]: no end-of-input flushing, just restore.
 */
class RestorableKeyedOperator[K, IN, OUT](
    function: KeyedProcessFunction[K, IN, OUT] with KeyedRestoreFunction[K, OUT]
) extends KeyedProcessOperator[K, IN, OUT](function)
    with RestorableKeyedOperatorMixin[K, OUT] {

  @transient private var wasRestored: Boolean = _

  override def initializeState(context: StateInitializationContext): Unit = {
    super.initializeState(context)
    wasRestored = context.isRestored
  }

  override def open(): Unit = {
    super.open()
    if (wasRestored) {
      restoreForAllKeys(function)
    }
  }

}

private[operator] trait RestorableKeyedOperatorMixin[K, OUT] {
  self: KeyedProcessOperator[K, _, OUT] =>

  protected def restoreForAllKeys(function: KeyedRestoreFunction[K, OUT]): Unit = {
    val timerService = getInternalTimerService("user-timers", VoidNamespaceSerializer.INSTANCE, this)
    getKeyedStateBackend[K].applyToAllKeys(
      VoidNamespace.INSTANCE,
      VoidNamespaceSerializer.INSTANCE,
      function.restoreStateDescriptor.asInstanceOf[StateDescriptor[State, _]],
      (key, _: State) => function.restoreForCurrentKey(key, timerService)
    )
  }

}
