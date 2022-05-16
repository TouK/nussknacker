package pl.touk.nussknacker.engine.util.metrics.common

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.{Context, EmptyProcessListener, MetaData}
import pl.touk.nussknacker.engine.util.SafeLazyValues
import pl.touk.nussknacker.engine.util.metrics.common.naming.nodeIdTag
import pl.touk.nussknacker.engine.util.metrics.{MetricIdentifier, RateMeter, WithMetrics}

private[engine] class EndCountingListener extends EmptyProcessListener with WithMetrics {

  private val endRateMeters = new SafeLazyValues[String, RateMeter]()

  override def deadEndEncountered(lastNodeId: String, context: Context, processMetaData: MetaData): Unit = {
    endRateMeters.getOrCreate(lastNodeId, () => metricsProvider.instantRateMeterWithCount(
      MetricIdentifier(NonEmptyList.of("dead_end"), Map(nodeIdTag -> lastNodeId)))).mark()
  }

  override def endEncountered(nodeId: String, ref: String, context: Context, processMetaData: MetaData): Unit = {
    endRateMeters.getOrCreate(nodeId, () => metricsProvider.instantRateMeterWithCount(
      MetricIdentifier(NonEmptyList.of("end"), Map(nodeIdTag -> nodeId)))).mark()
  }

}
