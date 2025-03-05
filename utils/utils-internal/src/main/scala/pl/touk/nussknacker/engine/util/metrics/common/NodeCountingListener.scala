package pl.touk.nussknacker.engine.util.metrics.common

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.{Context, EmptyProcessListener, MetaData}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.util.metrics.{Counter, MetricIdentifier, WithMetrics}
import pl.touk.nussknacker.engine.util.metrics.common.naming.nodeIdTag

private[engine] class NodeCountingListener(nodeIds: Iterable[String]) extends EmptyProcessListener with WithMetrics {

  private var counters: Map[String, Counter] = null

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    counters = nodeIds
      .map(nodeId =>
        nodeId -> metricsProvider.counter(MetricIdentifier(NonEmptyList.of("nodeCount"), Map(nodeIdTag -> nodeId)))
      )
      .toMap
  }

  override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData): Unit = {
    val counter = counters
      .getOrElse(nodeId, throw new RuntimeException(s"Unexpected node: ${nodeId}, known nodes: ${counters.keySet}"))
    counter.update(1)
  }

}
