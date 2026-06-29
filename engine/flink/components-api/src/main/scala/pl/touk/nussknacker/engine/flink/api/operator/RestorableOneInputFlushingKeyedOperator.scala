package pl.touk.nussknacker.engine.flink.api.operator

import org.apache.flink.runtime.state.StateInitializationContext
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.operators.{BoundedOneInput, KeyedProcessOperator}

/**
 * A [[KeyedProcessOperator]] composing two independent behaviors:
 * * after-restore per-key processing from [[RestorableKeyedOperator]]
 * * end-of-input flushing from [[OneInputFlushingKeyedOperator]]
 */
class RestorableOneInputFlushingKeyedOperator[K, IN, OUT](
    function: KeyedProcessFunction[K, IN, OUT] with KeyedRestoreFunction[K, OUT] with KeyedFlushFunction[K, OUT]
) extends KeyedProcessOperator[K, IN, OUT](function)
    with BoundedOneInput
    with RestorableKeyedOperatorMixin[K, OUT]
    with OneInputFlushingKeyedOperatorMixin[K, OUT] {

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

  override def endInput(): Unit =
    flushForAllKeys(function, output)

}
