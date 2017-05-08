package pl.touk.esp.engine.flink.util.listener

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.metrics.{Counter, MetricGroup}
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.flink.api.RuntimeContextLifecycle

class NodeCountMetricListener extends EmptyProcessListener with RuntimeContextLifecycle {

  @transient private var group : MetricGroup = _

  private val counters = collection.concurrent.TrieMap[String, Counter]()

  override def open(runtimeContext: RuntimeContext): Unit = {
    group = runtimeContext.getMetricGroup.addGroup("nodeCount")
  }

  override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData, mode: InterpreterMode): Unit = {
    if (mode == InterpreterMode.Traverse) {
      val counter = counters.getOrElseUpdate(nodeId, group.counter(nodeId))
      counter.inc()
    }
  }
}
