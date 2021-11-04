package pl.touk.nussknacker.engine.flink.util.listener

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.util.metrics.WithMetrics
import pl.touk.nussknacker.engine.util.metrics.MetricIdentifier

class NodeCountMetricListener extends EmptyProcessListener with WithMetrics {

  private val counters = collection.concurrent.TrieMap[String, Long => Unit]()

  override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData): Unit = {
    val counter = counters.getOrElseUpdate(nodeId, metricsProvider.counter(MetricIdentifier(
      NonEmptyList.of("nodeCount"), Map("nodeId" -> nodeId))))
    counter(1)
  }
}
