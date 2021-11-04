package pl.touk.nussknacker.engine.util.metrics

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.util.service.EspTimer

case class MetricIdentifier(name: NonEmptyList[String], tags: Map[String, String]) {
  def withNameSuffix(suffix: String): MetricIdentifier = copy(name = name :+ suffix)
}

trait MetricsProvider {

  def espTimer(identifier: MetricIdentifier, instantTimerWindowInSeconds: Long = 10): EspTimer

  def registerGauge[T](identifier: MetricIdentifier, value: () => T): Unit

  def counter(identifier: MetricIdentifier): Long => Unit

  def histogram(identifier: MetricIdentifier): Long => Unit

}

object NoOpMetricsProvider extends NoOpMetricsProvider

trait NoOpMetricsProvider extends MetricsProvider {

  override def espTimer(identifier: MetricIdentifier, instantTimerWindowInSeconds: Long): EspTimer =
    EspTimer(() => {}, _ => ())

  override def registerGauge[T](identifier: MetricIdentifier, value: () => T): Unit = {}

  override def counter(identifier: MetricIdentifier): Long => Unit = _ => {}

  override def histogram(identifier: MetricIdentifier): Long => Unit = _ => {}

}