package pl.touk.nussknacker.engine.standalone.utils.metrics.dropwizard

import java.util.concurrent.TimeUnit

import com.codahale.metrics._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.standalone.utils.metrics.MetricsProvider
import pl.touk.nussknacker.engine.util.service.EspTimer

class DropwizardMetricsProvider(metricRegistry: MetricRegistry) extends MetricsProvider with LazyLogging {

  override def espTimer(processId: String, instantTimerWindowInSeconds: Long, name: String*): EspTimer = {
    val histogram = new Histogram(new SlidingTimeWindowReservoir(instantTimerWindowInSeconds, TimeUnit.SECONDS))
    val registered = register(processId, MetricRegistry.name("serviceTimes", name: _*), histogram)
    val meter = register(processId, MetricRegistry.name("serviceInstant", name: _*), new InstantRateMeter)
    EspTimer(meter, registered.update)
  }

  override def close(processId: String): Unit = {
    metricRegistry.removeMatching(new MetricFilter {
      override def matches(name: String, metric: Metric): Boolean = name.startsWith(processId)
    })
  }

  //we want to be safe in concurrent conditions...
  def register[T <: Metric](processId: String, name: String, metric: T): T = {
    val metricName = MetricRegistry.name(processId, name)
    try {
      metricRegistry.register(metricName, metric)
    } catch {
      case e: IllegalArgumentException if e.getMessage == "A metric named " + metricName + " already exists" =>
        logger.info(s"Reusing existing metric for $name")
        metricRegistry.getMetrics.get(metricName).asInstanceOf[T]
    }
  }

}
