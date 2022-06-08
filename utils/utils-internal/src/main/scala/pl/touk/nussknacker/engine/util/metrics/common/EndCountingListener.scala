package pl.touk.nussknacker.engine.util.metrics.common

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.{Context, EmptyProcessListener, MetaData}
import pl.touk.nussknacker.engine.graph.node.{EndingNodeData, Filter, NodeData, Switch}
import pl.touk.nussknacker.engine.util.metrics.common.naming.nodeIdTag
import pl.touk.nussknacker.engine.util.metrics.{MetricIdentifier, RateMeter, WithMetrics}

private[engine] class EndCountingListener(allNodes: Iterable[NodeData]) extends EmptyProcessListener with WithMetrics {

  private var endRateMeters: Map[String, RateMeter] = null

  private var deadEndRateMeters: Map[String, RateMeter] = null

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    endRateMeters = allNodes.collect {
      case e:EndingNodeData => e.id
    }.map(nodeId => nodeId -> metricsProvider.instantRateMeterWithCount(
      MetricIdentifier(NonEmptyList.of("end"), Map(nodeIdTag -> nodeId)))).toMap
    deadEndRateMeters = allNodes.collect {
      case e: Switch => e.id
      case e: Filter => e.id
    }.map(nodeId => nodeId -> metricsProvider.instantRateMeterWithCount(
      MetricIdentifier(NonEmptyList.of("dead_end"), Map(nodeIdTag -> nodeId)))).toMap
  }

  override def deadEndEncountered(lastNodeId: String, context: Context, processMetaData: MetaData): Unit = {
    deadEndRateMeters.getOrElse(lastNodeId, throw new IllegalArgumentException(s"Unknown node ${lastNodeId} known: ${deadEndRateMeters}")).mark()
  }

  override def endEncountered(nodeId: String, ref: String, context: Context, processMetaData: MetaData): Unit = {
    endRateMeters.getOrElse(nodeId, throw new IllegalArgumentException(s"Unknown node ${nodeId} known: ${endRateMeters}")).mark()
  }

}
