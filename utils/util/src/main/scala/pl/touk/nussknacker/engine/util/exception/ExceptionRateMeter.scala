package pl.touk.nussknacker.engine.util.exception

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.util.metrics.{InstantRateMeterWithCount, MetricsProviderForScenario, RateMeter}

class ExceptionRateMeter(metricsProvider: MetricsProviderForScenario) {

  private lazy val allErrorsMeter: RateMeter = instantRateMeter(Map(), NonEmptyList.of("error", "instantRate"))

  private val nodeErrorsMeterMap = collection.concurrent.TrieMap[String, RateMeter]()

  def markException(exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit = {
    allErrorsMeter.mark()
    getMeterForNode(exceptionInfo.nodeId.getOrElse("unknown")).mark()
  }

  private def getMeterForNode(nodeId: String): RateMeter =
    nodeErrorsMeterMap.getOrElseUpdate(nodeId, instantRateMeter(Map("nodeId" -> nodeId), NonEmptyList.of("error", "instantRateByNode")))

  private def instantRateMeter(tags: Map[String, String], name: NonEmptyList[String]): RateMeter =
    InstantRateMeterWithCount.register(tags, name.toList, metricsProvider)

}
