package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import pl.touk.nussknacker.engine.api.{Context => NkContext}

import scala.collection.immutable.TreeMap

trait AddedElementContextStateHolder {

  // We keep tracking context of added elements, because during handling timer we need to emit some context with e.g. key prepared before aggregation.
  // Context of element that left the slide can be confusing but we haven't got anything better
  @transient
  protected var addedElementContext: ValueState[TreeMap[Long, NkContext]] = _

  protected def invalidateAddedElementContextState(stateValue: TreeMap[Long, AnyRef]): Unit = {
    addedElementContext.update(readAddedElementContextOrInitial().filter(kv => stateValue.contains(kv._1)))
  }

  protected def readAddedElementContextOrInitial(): TreeMap[Long, NkContext] =
    Option(addedElementContext.value()).getOrElse(TreeMap.empty(Ordering.Long))

  protected def addedElementContextDescriptor: ValueStateDescriptor[TreeMap[Long, NkContext]] =
    new ValueStateDescriptor[TreeMap[Long, NkContext]]("addedElementContext", classOf[TreeMap[Long, NkContext]])

}
