package pl.touk.nussknacker.engine.util.metrics.common

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.{Context, EmptyProcessListener, MetaData, NodeId, NodeName}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.graph.node.{DeadEndingData, EndingNodeData, NodeData}
import pl.touk.nussknacker.engine.util.metrics.{MetricIdentifier, RateMeter, WithMetrics}
import pl.touk.nussknacker.engine.util.metrics.common.naming.{nodeIdTag, nodeNameTag}

private[engine] class EndCountingListener(allNodes: Iterable[NodeData]) extends EmptyProcessListener with WithMetrics {

  private var endRateMeters: Meters = null

  private var deadEndRateMeters: Meters = null

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    endRateMeters = new Meters(
      "end",
      { case e: EndingNodeData =>
        (e.id, e.name)
      }
    )
    deadEndRateMeters = new Meters(
      "dead_end",
      { case e: DeadEndingData =>
        (e.id, e.name)
      }
    )
  }

  override def deadEndEncountered(
      lastNodeId: NodeId,
      context: Context,
      processMetaData: MetaData
  ): Unit = {
    deadEndRateMeters.mark(lastNodeId)
  }

  override def endEncountered(
      nodeId: NodeId,
      ref: String,
      context: Context,
      processMetaData: MetaData
  ): Unit = {
    endRateMeters.mark(nodeId)
  }

  private class Meters(name: String, nodeIdAndNames: PartialFunction[NodeData, (NodeId, NodeName)]) {

    val meters: Map[NodeId, RateMeter] = allNodes
      .collect(nodeIdAndNames)
      .map { case (nodeId, nodeName) =>
        nodeId -> metricsProvider.instantRateMeterWithCount(
          MetricIdentifier(NonEmptyList.of(name), Map(nodeIdTag -> nodeId.value, nodeNameTag -> nodeName.value))
        )
      }
      .toMap

    def mark(nodeId: NodeId): Unit = meters
      .getOrElse(nodeId, throw new IllegalArgumentException(s"Unknown node $nodeId in $name known: ${meters.keySet}"))
      .mark()

  }

}
