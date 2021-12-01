package pl.touk.nussknacker.engine.util.metrics.common

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.{Context, EmptyProcessListener, MetaData}
import pl.touk.nussknacker.engine.util.metrics.common.naming.nodeIdTag
import pl.touk.nussknacker.engine.util.metrics.{InstantRateMeterWithCount, RateMeter, SafeLazyMetrics, WithMetrics}

class EndCountingListener extends EmptyProcessListener with WithMetrics {

  private val endRateMeters = new SafeLazyMetrics[String, RateMeter]()

  override def deadEndEncountered(lastNodeId: String, context: Context, processMetaData: MetaData): Unit = {
    endRateMeters.getOrCreate(lastNodeId, () => instantRateMeter(Map(nodeIdTag -> lastNodeId), NonEmptyList.of("dead_end"))).mark()
  }

  override def sinkInvoked(nodeId: String, ref: String, context: Context, processMetaData: MetaData, param: Any): Unit = {
    endRateMeters.getOrCreate(nodeId, () => instantRateMeter(Map(nodeIdTag -> nodeId), NonEmptyList.of("end"))).mark()
  }

  private def instantRateMeter(tags: Map[String, String], name: NonEmptyList[String]): RateMeter =
    InstantRateMeterWithCount.register(tags, name.toList, metricsProvider)

}