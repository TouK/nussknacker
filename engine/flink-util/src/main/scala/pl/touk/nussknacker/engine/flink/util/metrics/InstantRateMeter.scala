package pl.touk.nussknacker.engine.flink.util.metrics

import org.apache.flink.metrics.{Counter, Gauge, MetricGroup}
import pl.touk.nussknacker.engine.util.metrics.RateMeter

class InstantRateMeter extends  pl.touk.nussknacker.engine.util.metrics.GenericInstantRateMeter with Gauge[Double]

object InstantRateMeterWithCount {

  def register(metricGroup: MetricGroup) : InstantRateMeterWithCount = {
    new InstantRateMeterWithCount(metricGroup.gauge[Double, InstantRateMeter]("instantRate", new InstantRateMeter), metricGroup.counter("count"))
  }
}

case class InstantRateMeterWithCount(rateMeter: InstantRateMeter, counter: Counter) extends RateMeter {

  override def mark(): Unit = {
    rateMeter.mark()
    counter.inc()
  }
}


