package pl.touk.nussknacker.engine.lite.kafka

import cats.data.NonEmptyList
import org.apache.kafka.common.{Metric, MetricName}
import pl.touk.nussknacker.engine.util.metrics.{Gauge, MetricIdentifier, MetricsProviderForScenario}

import scala.collection.mutable
import scala.jdk.CollectionConverters.mapAsScalaMapConverter

//We have to pass taskId, as we will need
private[kafka] class KafkaMetricsRegistrar(taskId: String, metrics: java.util.Map[MetricName, _ <: Metric], metricsProvider: MetricsProviderForScenario) extends AutoCloseable {

  private val registeredNames: mutable.Set[MetricIdentifier] = new mutable.HashSet[MetricIdentifier]()

  def registerMetrics(): Unit = {
    metrics.forEach { case (name, metric) =>
      val metricIdentifier = prepareMetricIdentifier(name)
      registeredNames.add(metricIdentifier)
      metricsProvider.registerGauge[AnyRef](metricIdentifier, new Gauge[AnyRef] {
        override def getValue: AnyRef = metric.metricValue()
      })
    }
  }

  override def close(): Unit = {
    registeredNames.foreach(metricsProvider.remove)
  }

  private def prepareMetricIdentifier(name: MetricName) = {
    val tags = name.tags().asScala.toMap + ("taskId" -> taskId) + ("kafkaGroup" -> name.group())
    MetricIdentifier(NonEmptyList.of(name.name()), tags)
  }

}
