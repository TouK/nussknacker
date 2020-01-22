package pl.touk.nussknacker.engine.flink.util.metrics

import cats.data.NonEmptyList
import org.apache.flink.metrics.{Counter, Gauge}
import pl.touk.nussknacker.engine.util.metrics.RateMeter

//TODO: add also "normal" rate meter here (such as in dropwizard etc)
class InstantRateMeter extends pl.touk.nussknacker.engine.util.metrics.GenericInstantRateMeter with Gauge[Double]

object InstantRateMeterWithCount {

  def register(tags: Map[String, String], name: List[String], metricUtils: MetricUtils) : InstantRateMeterWithCount = {
    val rateMeter = metricUtils.gauge[Double, InstantRateMeter](NonEmptyList.ofInitLast(name, "instantRate"), tags, new InstantRateMeter)
    val counter = metricUtils.counter(NonEmptyList.ofInitLast(name, "count"), tags)
    new InstantRateMeterWithCount(rateMeter, counter)
  }
}

case class InstantRateMeterWithCount(rateMeter: InstantRateMeter, counter: Counter) extends RateMeter {

  override def mark(): Unit = {
    rateMeter.mark()
    counter.inc()
  }
}


