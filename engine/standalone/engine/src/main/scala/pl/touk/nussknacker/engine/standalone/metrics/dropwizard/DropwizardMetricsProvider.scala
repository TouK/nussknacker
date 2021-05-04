package pl.touk.nussknacker.engine.standalone.metrics.dropwizard

import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.{Histogram, Metric, MetricFilter, MetricName, MetricRegistry, SlidingTimeWindowReservoir}
import pl.touk.nussknacker.engine.standalone.api.metrics.MetricsProvider
import pl.touk.nussknacker.engine.util.service.EspTimer

import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._

class DropwizardMetricsProvider(metricRegistry: MetricRegistry) extends MetricsProvider with LazyLogging {

  override def espTimer(processId: String, instantTimerWindowInSeconds: Long, tags: Map[String, String], name: NonEmptyList[String]): EspTimer = {
    val histogram = new Histogram(new SlidingTimeWindowReservoir(instantTimerWindowInSeconds, TimeUnit.SECONDS))
    val registered = register(processId, tags, name :+ EspTimer.histogramSuffix, histogram)
    val meter = register(processId, tags, name :+ EspTimer.instantRateSuffix, new InstantRateMeter)
    EspTimer(meter, registered.update)
  }

  override def close(processId: String): Unit = {
    metricRegistry.removeMatching(new MetricFilter {
      override def matches(name: MetricName, metric: Metric): Boolean = name.getTags.get("processId") == processId
    })
  }

  //we want to be safe in concurrent conditions...
  def register[T <: Metric](processId: String, tags: Map[String, String], name: NonEmptyList[String], metric: T): T = {
    val metricName = MetricRegistry.name(name.head, name.tail: _*)
      .tagged(tags.asJava)
      .tagged("processId", processId)
    try {
      metricRegistry.register( metricName.tagged("processId", processId), metric)
    } catch {
      case e: IllegalArgumentException if e.getMessage == "A metric named " + metricName + " already exists" =>
        logger.info(s"""Reusing existing metric for $metricName""")
        metricRegistry.getMetrics.get(metricName).asInstanceOf[T]
    }
  }

}
