package pl.touk.nussknacker.engine.util.metrics.common

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.util.metrics._
import pl.touk.nussknacker.engine.util.metrics.common.naming.nodeIdTag

import java.time.Clock
import java.util.concurrent.atomic.AtomicLong

private[engine] class OneSourceMetrics(sourceId: String, clock: Clock = Clock.systemDefaultZone()) {

  private val tags            = Map(nodeIdTag -> sourceId)
  private val lastElementTime = new AtomicLong(0)

  private var registeredMetricsOpt = Option.empty[OneSourceRegisteredMetrics]

  def registerOwnMetrics(metricsProvider: MetricsProviderForScenario): Unit = {
    val timer       = metricsProvider.histogram(MetricIdentifier(NonEmptyList.of("eventtimedelay", "histogram"), tags))
    val instantRate = metricsProvider.instantRateMeterWithCount(MetricIdentifier(NonEmptyList.of("source"), tags))
    val minimalDelayGauge = new Gauge[Long] {
      override def getValue: Long = minimalDelayValue()
    }
    metricsProvider.registerGauge(
      MetricIdentifier(NonEmptyList.of("eventtimedelay", "minimalDelay"), tags),
      minimalDelayGauge
    )
    registeredMetricsOpt = Some(OneSourceRegisteredMetrics(timer, instantRate, minimalDelayGauge))
  }

  def process(elementTimestamp: Long): Unit = {
    val registeredMetrics = registeredMetricsOpt.getOrElse(
      throw new IllegalStateException("registerMetrics not called - metrics should be registered before usage")
    )
    registeredMetrics.timer.update(clock.millis() - elementTimestamp)
    lastElementTime.updateAndGet(math.max(elementTimestamp, _))
    registeredMetrics.instantRate.mark()
  }

  private def minimalDelayValue(): Long = {
    clock.millis() - lastElementTime.get()
  }

  private case class OneSourceRegisteredMetrics(
      timer: Histogram,
      instantRate: RateMeter,
      minimalDelayGauge: Gauge[Long]
  )

}
