package pl.touk.nussknacker.engine.util.metrics.common

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.{Context, EmptyProcessListener, MetaData}
import pl.touk.nussknacker.engine.util.metrics.common.naming.nodeIdTag
import pl.touk.nussknacker.engine.util.metrics.{Counter, MetricIdentifier, WithMetrics}

class NodeCountingListener extends EmptyProcessListener with WithMetrics {

  private val counters = collection.concurrent.TrieMap[String, Counter]()

  override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData): Unit = {
    val counter = counters.getOrElseUpdate(nodeId, metricsProvider.counter(MetricIdentifier(
      NonEmptyList.of("nodeCount"), Map(nodeIdTag -> nodeId))))
    counter.update(1)
  }

}
