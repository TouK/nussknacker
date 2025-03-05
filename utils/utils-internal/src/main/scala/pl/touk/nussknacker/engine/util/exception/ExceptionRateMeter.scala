package pl.touk.nussknacker.engine.util.exception

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.util.metrics.{MetricIdentifier, MetricsProviderForScenario, RateMeter}
import pl.touk.nussknacker.engine.util.metrics.common.naming.nodeIdTag

class ExceptionRateMeter(metricsProvider: MetricsProviderForScenario) {

  private lazy val allErrorsMeter: RateMeter =
    metricsProvider.instantRateMeterWithCount(MetricIdentifier(NonEmptyList.of("error", "instantRate"), Map.empty))

  // This meter will not be eagerly initialized
  private val nodeErrorsMeterMap = collection.concurrent.TrieMap[String, RateMeter]()

  def markException(exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit = {
    allErrorsMeter.mark()
    getMeterForNode(exceptionInfo.nodeComponentInfo.map(_.nodeId).getOrElse("unknown")).mark()
  }

  private def getMeterForNode(nodeId: String): RateMeter = nodeErrorsMeterMap.getOrElseUpdate(
    nodeId,
    metricsProvider.instantRateMeterWithCount(
      MetricIdentifier(NonEmptyList.of("error", "instantRateByNode"), Map(nodeIdTag -> nodeId))
    )
  )

}
