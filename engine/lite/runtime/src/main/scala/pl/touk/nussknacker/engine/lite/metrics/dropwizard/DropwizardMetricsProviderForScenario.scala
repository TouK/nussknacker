package pl.touk.nussknacker.engine.lite.metrics.dropwizard

import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5
import io.dropwizard.metrics5.{Metric, MetricName, MetricRegistry, SlidingTimeWindowReservoir}
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.DropwizardMetricsProviderForScenario.scenarioTagName
import pl.touk.nussknacker.engine.util.metrics._
import pl.touk.nussknacker.engine.util.service.EspTimer

import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._

class DropwizardMetricsProviderFactory(metricRegistry: MetricRegistry) extends (String => MetricsProviderForScenario with AutoCloseable) {
  override def apply(scenarioId: String): MetricsProviderForScenario with AutoCloseable = new DropwizardMetricsProviderForScenario(scenarioId, metricRegistry)
}

object DropwizardMetricsProviderForScenario {

  val scenarioTagName = "process"

}

class DropwizardMetricsProviderForScenario(scenarioId: String, metricRegistry: MetricRegistry) extends MetricsProviderForScenario with AutoCloseable with LazyLogging {

  //This method can be invoked only once for given identifier
  override def espTimer(metricIdentifier: MetricIdentifier, instantTimerWindowInSeconds: Long = 10): EspTimer = {
    val histogramInstance = histogram(metricIdentifier.withNameSuffix(EspTimer.histogramSuffix))
    val meter = new InstantRateMeter with metrics5.Gauge[Double]
    registerGauge(metricIdentifier.withNameSuffix(EspTimer.instantRateSuffix), meter)
    EspTimer(meter, histogramInstance)
  }

  override def counter(metricIdentifier: MetricIdentifier): Counter = {
    val counter = register(metricIdentifier, new metrics5.Counter, reuseIfExisting = true)
    counter.inc _
  }

  override def histogram(metricIdentifier: MetricIdentifier, instantTimerWindowInSeconds: Long = 10): Histogram = {
    val reservoir = new SlidingTimeWindowReservoir(instantTimerWindowInSeconds, TimeUnit.SECONDS)
    val histogram = register(metricIdentifier, new metrics5.Histogram(reservoir), reuseIfExisting = true)
    histogram.update _
  }

  //For most cases it's possible to reuse existing metric when there is concurrent addition of metrics
  //(MetricRegistry is backed by ConcurrentMap), so we return existing one for counters, histograms etc.
  private def register[T <: Metric](id: MetricIdentifier, metric: T, reuseIfExisting: Boolean): T = {
    val metricName = MetricRegistry.name(id.name.head, id.name.tail: _*)
      .tagged(id.tags.asJava)
      .tagged(scenarioTagName, scenarioId)
    try {
      metricRegistry.register(metricName, metric)
    } catch {
      case e: IllegalArgumentException if reuseIfExisting && e.getMessage == "A metric named " + metricName + " already exists" =>
        logger.info(s"""Reusing existing metric for $metricName""")
        metricRegistry.getMetrics.get(metricName).asInstanceOf[T]
    }
  }

  override def registerGauge[T](metricIdentifier: MetricIdentifier, gauge: Gauge[T]): Unit = {
    //We cannot just accept conflicting gauges...
    register[metrics5.Gauge[T]](metricIdentifier, gauge.getValue _, reuseIfExisting = false)
  }

  override def close(): Unit = {
    metricRegistry.removeMatching((name: MetricName, _: Metric) => name.getTags.get(scenarioTagName) == scenarioId)
  }
}