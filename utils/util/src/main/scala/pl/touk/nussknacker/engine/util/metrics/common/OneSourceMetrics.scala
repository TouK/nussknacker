package pl.touk.nussknacker.engine.util.metrics.common

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.util.metrics.common.naming.nodeIdTag
import pl.touk.nussknacker.engine.util.metrics.{Gauge, InstantRateMeterWithCount, MetricIdentifier, MetricsProviderForScenario}

import java.time.Clock
import java.util.concurrent.atomic.AtomicLong

class OneSourceMetrics(metricsProvider: MetricsProviderForScenario, sourceId: String, clock: Clock = Clock.systemDefaultZone()) {

  private val tags = Map(nodeIdTag -> sourceId)
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
