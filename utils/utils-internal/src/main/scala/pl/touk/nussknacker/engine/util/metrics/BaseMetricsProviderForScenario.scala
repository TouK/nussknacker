package pl.touk.nussknacker.engine.util.metrics

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.util.service.EspTimer

trait BaseMetricsProviderForScenario extends MetricsProviderForScenario {

  override def espTimer(identifier: MetricIdentifier, instantTimerWindowInSeconds: Long): EspTimer = {
    val instantRateMeter = new InstantRateMeter
    registerGauge(identifier.withNameSuffix(EspTimer.instantRateSuffix), instantRateMeter)
    val registered = histogram(identifier.withNameSuffix(EspTimer.histogramSuffix), instantTimerWindowInSeconds)
    EspTimer(instantRateMeter, registered)
  }

  override def instantRateMeterWithCount(identifier: MetricIdentifier): RateMeter = {
    val rateMeterInstance = new InstantRateMeter
    val name = identifier.name.toList
    val tags = identifier.tags
    registerGauge[Double](MetricIdentifier(NonEmptyList.ofInitLast(name, "instantRate"), tags), rateMeterInstance)
    val counterObj = counter(MetricIdentifier(NonEmptyList.ofInitLast(name, "count"), tags))
    InstantRateMeterWithCount(rateMeterInstance, counterObj)
  }

}
