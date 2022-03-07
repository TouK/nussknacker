package pl.touk.nussknacker.engine.util.metrics

import pl.touk.nussknacker.engine.util.service.EspTimer

object NoOpMetricsProviderForScenario extends NoOpMetricsProviderForScenario

trait NoOpMetricsProviderForScenario extends MetricsProviderForScenario {

  override def espTimer(identifier: MetricIdentifier, instantTimerWindowInSeconds: Long): EspTimer =
    EspTimer(() => {}, _ => ())

  override def instantRateMeterWithCount(identifier: MetricIdentifier): RateMeter = () => {}

  override def registerGauge[T](identifier: MetricIdentifier, value: Gauge[T]): Unit = {}

  override def counter(identifier: MetricIdentifier): Counter = _ => {}

  override def histogram(identifier: MetricIdentifier, instantTimerWindowInSeconds: Long): Histogram = _ => {}

  override def remove(metricIdentifier: MetricIdentifier): Unit = {}

}