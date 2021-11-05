package pl.touk.nussknacker.engine.flink.util.listener

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.util.metrics.WithMetrics
import pl.touk.nussknacker.engine.util.metrics.{Counter, MetricIdentifier}

class NodeCountMetricListener extends EmptyProcessListener with WithMetrics {

  private val counters = collection.concurrent.TrieMap[String, Counter]()

  override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData): Unit = {
    val counter = counters.getOrElseUpdate(nodeId, metricsProvider.counter(MetricIdentifier(
      NonEmptyList.of("nodeCount"), Map("nodeId" -> nodeId))))
    counter.update(1)
  }
}
