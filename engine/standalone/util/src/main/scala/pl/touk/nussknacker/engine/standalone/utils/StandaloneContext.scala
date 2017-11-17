package pl.touk.nussknacker.engine.standalone.utils

import com.codahale.metrics.{Metric, MetricFilter, MetricRegistry}
import com.typesafe.scalalogging.LazyLogging

case class StandaloneContext(processId: String, private val metricRegistry: MetricRegistry) extends LazyLogging {

  //we want to be safe in concurrent conditions
  def register[T<:Metric](name: String, metric: T) : T = {
    val metricName = MetricRegistry.name(processId, name)
    try {
      metricRegistry.register(metricName, metric)
    } catch {
      case e:IllegalArgumentException if e.getMessage == "A metric named " + metricName + " already exists" =>
        logger.info(s"Reusing existing metric for $name")
        metricRegistry.getMetrics.get(metricName).asInstanceOf[T]
    }
  }

  def close(): Unit = {
    metricRegistry.removeMatching(new MetricFilter {
      override def matches(name: String, metric: Metric) = name.startsWith(processId)
    })
  }

}

class StandaloneContextPreparer(metricRegistry: MetricRegistry) {
  def prepare(processId: String) = StandaloneContext(processId, metricRegistry)
}
