package pl.touk.nussknacker.engine.baseengine.metrics

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.baseengine.api.interpreterTypes.SourceId
import pl.touk.nussknacker.engine.util.metrics.{Gauge, InstantRateMeterWithCount, MetricIdentifier, MetricsProviderForScenario}

import java.time.Clock

class SourceMetrics(metricsProvider: MetricsProviderForScenario, clock: Clock = Clock.systemDefaultZone()) {

  private val sourceMetrics = collection.concurrent.TrieMap[SourceId, MetricsForOneSource]()

  def markElement(sourceId: SourceId, elementTimestamp: Long): Unit = {
    sourceMetrics.getOrElseUpdate(sourceId, new MetricsForOneSource(sourceId)).process(elementTimestamp)
  }

  class MetricsForOneSource(sourceId: SourceId) {
    private val tags = Map("nodeId" -> sourceId.value)
    private val timer = metricsProvider.histogram(MetricIdentifier(NonEmptyList.of("eventtimedelay", "histogram"), tags))
    private val instantRate = InstantRateMeterWithCount.register(tags, List("source"), metricsProvider)
    private var lastElementTime: Option[Long] = None

    {
      metricsProvider.registerGauge(MetricIdentifier(NonEmptyList.of("eventtimedelay", "minimalDelay"), tags), new Gauge[Long] {
        override def getValue: Long = minimalDelayValue()
      })
    }

    def process(elementTimestamp: Long): Unit = {
      timer.update(clock.millis() - elementTimestamp)
      lastElementTime = Some(lastElementTime.fold(elementTimestamp)(math.max(_, elementTimestamp)))
      instantRate.mark()
    }

    private def minimalDelayValue(): Long = {
      lastElementTime.map(clock.millis() - _).getOrElse(0)
    }

  }

}
