package pl.touk.nussknacker.engine.baseengine.metrics

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.baseengine.api.interpreterTypes.SourceId
import pl.touk.nussknacker.engine.util.metrics.{Gauge, InstantRateMeterWithCount, MetricIdentifier, MetricsProviderForScenario}

import java.time.Clock
import java.util.concurrent.atomic.AtomicLong
import javax.annotation.concurrent.ThreadSafe

@ThreadSafe
class SourceMetrics(metricsProvider: MetricsProviderForScenario,
                    sourceIds: Iterable[SourceId],
                    clock: Clock = Clock.systemDefaultZone()) {

  private val sourceMetrics = sourceIds.map(sourceId => sourceId -> new MetricsForOneSource(sourceId)).toMap

  def markElement(sourceId: SourceId, elementTimestamp: Long): Unit = {
    sourceMetrics(sourceId).process(elementTimestamp)
  }

  private class MetricsForOneSource(sourceId: SourceId) {
    private val tags = Map("nodeId" -> sourceId.value)
    private val timer = metricsProvider.histogram(MetricIdentifier(NonEmptyList.of("eventtimedelay", "histogram"), tags))
    private val instantRate = InstantRateMeterWithCount.register(tags, List("source"), metricsProvider)
    private val lastElementTime = new AtomicLong(0)

    {
      metricsProvider.registerGauge(MetricIdentifier(NonEmptyList.of("eventtimedelay", "minimalDelay"), tags), new Gauge[Long] {
        override def getValue: Long = minimalDelayValue()
      })
    }

    def process(elementTimestamp: Long): Unit = {
      timer.update(clock.millis() - elementTimestamp)
      lastElementTime.updateAndGet(math.max(elementTimestamp, _))
      instantRate.mark()
    }

    private def minimalDelayValue(): Long = {
      clock.millis() - lastElementTime.get()
    }

  }

}
