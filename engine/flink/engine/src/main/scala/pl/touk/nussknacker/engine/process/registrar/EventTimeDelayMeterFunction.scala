package pl.touk.nussknacker.engine.process.registrar

import java.util.concurrent.TimeUnit

import cats.data.NonEmptyList
import com.codahale.metrics.{Histogram, SlidingTimeWindowReservoir}
import org.apache.flink.configuration.Configuration
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper
import org.apache.flink.metrics.Gauge
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.flink.util.metrics.MetricUtils

import scala.concurrent.duration.FiniteDuration

class EventTimeDelayMeterFunction[T](groupId: String, nodeId: String, slidingWindow: FiniteDuration) extends ProcessFunction[T, T] {

  lazy val histogramMeter = new DropwizardHistogramWrapper(
    new Histogram(new SlidingTimeWindowReservoir(slidingWindow.toMillis, TimeUnit.MILLISECONDS)))

  lazy val minimalDelayGauge: Gauge[Long] = new Gauge[Long] {
    override def getValue: Long = {
      val now = System.currentTimeMillis()
      now - lastElementTime.getOrElse(now)
    }
  }

  var lastElementTime: Option[Long] = None

  override def open(parameters: Configuration): Unit = {
    val metrics = new MetricUtils(getRuntimeContext)
    metrics.histogram(NonEmptyList.of(groupId, "histogram"), Map("nodeId" -> nodeId), histogramMeter)
    metrics.gauge[Long, Gauge[Long]](NonEmptyList.of(groupId, "minimalDelay"), Map("nodeId" -> nodeId), minimalDelayGauge)
  }

  override def processElement(value: T, ctx: ProcessFunction[T, T]#Context, out: Collector[T]): Unit = {
    Option(ctx.timestamp()).foreach { timestamp =>
      val delay = System.currentTimeMillis() - timestamp
      histogramMeter.update(delay)
      lastElementTime = Some(lastElementTime.fold(timestamp)(math.max(_, timestamp)))
    }
    out.collect(value)
  }

}

