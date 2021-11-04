package pl.touk.nussknacker.engine.baseengine.metrics.dropwizard

import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5._
import pl.touk.nussknacker.engine.util.metrics.{MetricIdentifier, MetricsProvider}
import pl.touk.nussknacker.engine.util.service.EspTimer

import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._

class DropwizardMetricsProviderFactory(metricRegistry: MetricRegistry) extends (String => MetricsProvider with AutoCloseable) {
  override def apply(scenarioId: String): MetricsProvider with AutoCloseable = new DropwizardMetricsProvider(scenarioId, metricRegistry)
}

class DropwizardMetricsProvider(scenarioId: String, metricRegistry: MetricRegistry) extends MetricsProvider with AutoCloseable with LazyLogging {

  override def espTimer(metricIdentifier: MetricIdentifier, instantTimerWindowInSeconds: Long = 10): EspTimer = {
    val histogram = new Histogram(new SlidingTimeWindowReservoir(instantTimerWindowInSeconds, TimeUnit.SECONDS))
    val registered = register(metricIdentifier.withNameSuffix(EspTimer.histogramSuffix), histogram)
    val meter = register(metricIdentifier.withNameSuffix(EspTimer.instantRateSuffix), new InstantRateMeter)
    EspTimer(meter, registered.update)
  }

  override def counter(metricIdentifier: MetricIdentifier): Long => Unit = {
    val counter = register(metricIdentifier, new Counter)
    incBy => counter.inc(incBy)
  }

  override def histogram(metricIdentifier: MetricIdentifier): Long => Unit = {
    val histogram = register(metricIdentifier, new Histogram(new SlidingTimeWindowReservoir(10, TimeUnit.SECONDS)))
    incBy => histogram.update(incBy)
  }

  //we want to be safe in concurrent conditions...
  def register[T <: Metric](id: MetricIdentifier, metric: T): T = {
    val metricName = MetricRegistry.name(id.name.head, id.name.tail: _*)
      .tagged(id.tags.asJava)
      .tagged("processId", scenarioId)
      .tagged("process", scenarioId)
    try {
      metricRegistry.register(metricName, metric)
    } catch {
      case e: IllegalArgumentException if e.getMessage == "A metric named " + metricName + " already exists" =>
        logger.info(s"""Reusing existing metric for $metricName""")
        metricRegistry.getMetrics.get(metricName).asInstanceOf[T]
    }
  }

  override def registerGauge[T](metricIdentifier: MetricIdentifier, value: () => T): Unit = {
    register(metricIdentifier, new Gauge[T] {
      override def getValue: T = value()
    })
  }

  override def close(): Unit = {
    metricRegistry.removeMatching((name: MetricName, _: Metric) => name.getTags.get("processId") == scenarioId)
  }
}