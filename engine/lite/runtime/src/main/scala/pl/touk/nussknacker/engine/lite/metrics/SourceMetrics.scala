package pl.touk.nussknacker.engine.lite.metrics

import pl.touk.nussknacker.engine.lite.api.interpreterTypes.SourceId
import pl.touk.nussknacker.engine.util.metrics.MetricsProviderForScenario
import pl.touk.nussknacker.engine.util.metrics.common.OneSourceMetrics

import javax.annotation.concurrent.ThreadSafe

@ThreadSafe
class SourceMetrics(sourceIds: Iterable[SourceId]) {

  private val sourceMetrics = sourceIds.map(sourceId =>
    sourceId -> new OneSourceMetrics(sourceId.value)).toMap

  def registerOwnMetrics(metricsProvider: MetricsProviderForScenario): Unit = {
    sourceMetrics.values.foreach(_.registerOwnMetrics(metricsProvider))
  }

  def markElement(sourceId: SourceId, elementTimestamp: Long): Unit = {
    sourceMetrics(sourceId).process(elementTimestamp)
  }

}
