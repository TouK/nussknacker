package pl.touk.nussknacker.engine.util.metrics

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.util.service.EspTimer

case class MetricIdentifier(name: NonEmptyList[String], tags: Map[String, String]) {
  def withNameSuffix(suffix: String): MetricIdentifier = copy(name = name :+ suffix)
}

trait MetricsProviderForScenario {

  val defaultInstantTimerWindowInSeconds = 10

  def espTimer(identifier: MetricIdentifier, instantTimerWindowInSeconds: Long = defaultInstantTimerWindowInSeconds): EspTimer

  def registerGauge[T](identifier: MetricIdentifier, value: Gauge[T]): Unit

  def counter(identifier: MetricIdentifier): Counter

  def histogram(identifier: MetricIdentifier, instantTimerWindowInSeconds: Long = defaultInstantTimerWindowInSeconds): Histogram

}

trait Gauge[T] {
  def getValue: T
}

trait Counter {
  def update(value: Long): Unit
}

trait Histogram {
  def update(value: Long): Unit
}

object NoOpMetricsProviderForScenario extends NoOpMetricsProviderForScenario

trait NoOpMetricsProviderForScenario extends MetricsProviderForScenario {

  override def espTimer(identifier: MetricIdentifier, instantTimerWindowInSeconds: Long): EspTimer =
    EspTimer(() => {}, _ => ())

  override def registerGauge[T](identifier: MetricIdentifier, value: Gauge[T]): Unit = {}

  override def counter(identifier: MetricIdentifier): Counter = _ => {}

  override def histogram(identifier: MetricIdentifier, instantTimerWindowInSeconds: Long): Histogram = _ => {}

}