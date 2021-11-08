package pl.touk.nussknacker.engine.util.metrics

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.util.service.EspTimer

//Please note that additional tags will can be added by engine (scenarioId, slot/task, host etc.) so this is not
//full identifier as it will appear in monitoring system
case class MetricIdentifier(name: NonEmptyList[String], tags: Map[String, String]) {
  def withNameSuffix(suffix: String): MetricIdentifier = copy(name = name :+ suffix)
}

/**
  * Abstraction for metrics frameworks used by different scenario engines (e.g. Flink metrics and Dropwizard).
  * Metrics instances created/registered by this trait
  * represent metric for particular scenario and task instance (e.g. particular slot for Flink).
  * Method calls are *not* idempotent at the moment, so it's callers responsibility to call them only once for one metric during
  * lifecycle (e.g. during open in Lifecycle)
  */
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