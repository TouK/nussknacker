package pl.touk.nussknacker.engine.util.metrics

import cats.data.NonEmptyList

object InstantRateMeterWithCount {

  def register(tags: Map[String, String], name: List[String], metricsProvider: MetricsProviderForScenario) : InstantRateMeterWithCount = {
    val rateMeterInstance = new InstantRateMeter
    metricsProvider.registerGauge[Double](MetricIdentifier(NonEmptyList.ofInitLast(name, "instantRate"), tags), rateMeterInstance)
    val counter = metricsProvider.counter(MetricIdentifier(NonEmptyList.ofInitLast(name, "count"), tags))
    new InstantRateMeterWithCount(rateMeterInstance, counter)
  }
}

case class InstantRateMeterWithCount(rateMeter: InstantRateMeter, counter: Counter) extends RateMeter {

  override def mark(): Unit = {
    rateMeter.mark()
    counter.update(1)
  }
}