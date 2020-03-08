package pl.touk.nussknacker.engine.flink.util.listener

import cats.data.NonEmptyList
import org.apache.flink.metrics.Counter
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.util.metrics.WithMetrics

class NodeCountMetricListener extends EmptyProcessListener with WithMetrics {

  private val counters = collection.concurrent.TrieMap[String, Counter]()

  override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData): Unit = {
    val counter = counters.getOrElseUpdate(nodeId, metricUtils.counter(NonEmptyList.of("nodeCount"), Map("nodeId" -> nodeId)))
    counter.inc()
  }
}
