package pl.touk.nussknacker.engine.util.metrics.common

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.{Context, EmptyProcessListener, MetaData, NodeId, NodeName}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.util.metrics.{Counter, MetricIdentifier, WithMetrics}
import pl.touk.nussknacker.engine.util.metrics.common.naming.{nodeIdTag, nodeNameTag}

private[engine] class NodeCountingListener(nodes: Map[NodeId, NodeName]) extends EmptyProcessListener with WithMetrics {

  private var counters: Map[NodeId, Counter] = null

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    counters = nodes
      .map { case (nodeId, nodeName) =>
        nodeId -> metricsProvider.counter(
          MetricIdentifier(NonEmptyList.of("nodeCount"), Map(nodeIdTag -> nodeId.value, nodeNameTag -> nodeName.value))
        )
      }
  }

  override def nodeEntered(nodeId: NodeId, context: Context, processMetaData: MetaData): Unit = {
    val counter = counters
      .getOrElse(nodeId, throw new RuntimeException(s"Unexpected node: ${nodeId}, known nodes: ${counters.keySet}"))
    counter.update(1)
  }

}
