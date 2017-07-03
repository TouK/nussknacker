package pl.touk.esp.engine.standalone.utils

import com.codahale.metrics.{Metric, MetricFilter, MetricRegistry}

case class StandaloneContext(processId: String, private val metricRegistry: MetricRegistry) {

  def register[T<:Metric](name: String, metric: T) = metricRegistry.register(MetricRegistry.name(processId, name), metric)

  def close(): Unit = {
    metricRegistry.removeMatching(new MetricFilter {
      override def matches(name: String, metric: Metric) = name.startsWith(processId)
    })
  }

}

class StandaloneContextPreparer(metricRegistry: MetricRegistry) {
  def prepare(processId: String) = StandaloneContext(processId, metricRegistry)
}
