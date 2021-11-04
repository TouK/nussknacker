package pl.touk.nussknacker.engine.flink.util.metrics

import cats.data.NonEmptyList
import org.apache.flink.metrics.Gauge
import pl.touk.nussknacker.engine.util.metrics.{MetricIdentifier, MetricsProvider, RateMeter}

//TODO: add also "normal" rate meter here (such as in dropwizard etc)
class InstantRateMeter extends pl.touk.nussknacker.engine.util.metrics.GenericInstantRateMeter with Gauge[Double]

object InstantRateMeterWithCount {

  def register(tags: Map[String, String], name: List[String], metricUtils: MetricsProvider) : InstantRateMeterWithCount = {
    val rateMeterInstance = new InstantRateMeter
    metricUtils.registerGauge[Double](MetricIdentifier(NonEmptyList.ofInitLast(name, "instantRate"), tags), rateMeterInstance.getValue _)
    val counter = metricUtils.counter(MetricIdentifier(NonEmptyList.ofInitLast(name, "count"), tags))
    new InstantRateMeterWithCount(rateMeterInstance, counter)
  }
}

case class InstantRateMeterWithCount(rateMeter: InstantRateMeter, counter: Long => Unit) extends RateMeter {

  override def mark(): Unit = {
    rateMeter.mark()
    counter(1)
  }
}


