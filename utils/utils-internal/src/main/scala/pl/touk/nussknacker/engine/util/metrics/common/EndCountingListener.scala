package pl.touk.nussknacker.engine.util.metrics.common

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.{Context, EmptyProcessListener, MetaData}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.graph.node.{DeadEndingData, EndingNodeData, NodeData}
import pl.touk.nussknacker.engine.util.metrics.{MetricIdentifier, RateMeter, WithMetrics}
import pl.touk.nussknacker.engine.util.metrics.common.naming.nodeIdTag

private[engine] class EndCountingListener(allNodes: Iterable[NodeData]) extends EmptyProcessListener with WithMetrics {

  private var endRateMeters: Meters = null

  private var deadEndRateMeters: Meters = null

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    endRateMeters = new Meters(
      "end",
      { case e: EndingNodeData =>
        e.id
      }
    )
    deadEndRateMeters = new Meters(
      "dead_end",
      { case e: DeadEndingData =>
        e.id
      }
    )
  }

  override def deadEndEncountered(
      lastNodeId: String,
      context: Context,
      processMetaData: MetaData
  ): Unit = {
    deadEndRateMeters.mark(lastNodeId)
  }

  override def endEncountered(
      nodeId: String,
      ref: String,
      context: Context,
      processMetaData: MetaData
  ): Unit = {
    endRateMeters.mark(nodeId)
  }

  private class Meters(name: String, nodeIds: PartialFunction[NodeData, String]) {

    val meters: Map[String, RateMeter] = allNodes
      .collect(nodeIds)
      .map { nodeId =>
        nodeId -> metricsProvider.instantRateMeterWithCount(
          MetricIdentifier(NonEmptyList.of(name), Map(nodeIdTag -> nodeId))
        )
      }
      .toMap

    def mark(nodeId: String): Unit = meters
      .getOrElse(nodeId, throw new IllegalArgumentException(s"Unknown node $nodeId in $name known: ${meters.keySet}"))
      .mark()

  }

}
