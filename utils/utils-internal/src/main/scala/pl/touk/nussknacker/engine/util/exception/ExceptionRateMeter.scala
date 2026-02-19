package pl.touk.nussknacker.engine.util.exception

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.util.metrics.{MetricIdentifier, MetricsProviderForScenario, RateMeter}
import pl.touk.nussknacker.engine.util.metrics.common.naming.nodeIdTag

class ExceptionRateMeter(metricsProvider: MetricsProviderForScenario) {

  private lazy val allErrorsMeter: RateMeter =
    metricsProvider.instantRateMeterWithCount(MetricIdentifier(NonEmptyList.of("error", "instantRate"), Map.empty))

  // This meter will not be eagerly initialized
  private val nodeErrorsMeterMap = collection.concurrent.TrieMap[NodeId, RateMeter]()

  def markException(exceptionInfo: NuExceptionInfo): Unit = {
    allErrorsMeter.mark()
    getMeterForNode(exceptionInfo.nodeComponentInfo.map(_.nodeId).getOrElse(NodeId("unknown"))).mark()
  }

  private def getMeterForNode(nodeId: NodeId): RateMeter = nodeErrorsMeterMap.getOrElseUpdate(
    nodeId,
    metricsProvider.instantRateMeterWithCount(
      MetricIdentifier(NonEmptyList.of("error", "instantRateByNode"), Map(nodeIdTag -> nodeId.value))
    )
  )

}
