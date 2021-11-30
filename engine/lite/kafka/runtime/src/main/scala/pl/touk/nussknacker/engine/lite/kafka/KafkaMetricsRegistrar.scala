package pl.touk.nussknacker.engine.lite.kafka

import cats.data.NonEmptyList
import org.apache.kafka.common.{Metric, MetricName}
import pl.touk.nussknacker.engine.util.metrics.{Gauge, MetricIdentifier, MetricsProviderForScenario}

import scala.jdk.CollectionConverters.mapAsScalaMapConverter

//We have to pass taskId, as we will need
private[kafka] class KafkaMetricsRegistrar(taskId: String, metrics: java.util.Map[MetricName, _ <: Metric], metricsProvider: MetricsProviderForScenario) extends AutoCloseable {

  def registerMetrics(): Unit = {
    metrics.forEach { case (name, metric) =>
      val metricIdentifier = prepareMetricIdentifier(name)
      metricsProvider.registerGauge[AnyRef](metricIdentifier, new Gauge[AnyRef] {
        override def getValue: AnyRef = metric.metricValue()
      })
    }
  }

  override def close(): Unit = {
    metrics.forEach { case (name, _) =>
      val metricIdentifier = prepareMetricIdentifier(name)
      metricsProvider.remove(metricIdentifier)
    }
  }

  private def prepareMetricIdentifier(name: MetricName) = {
    val tags = name.tags().asScala.toMap + ("taskId" -> taskId) + ("kafkaGroup" -> name.group())
    val metricIdentifier = MetricIdentifier(NonEmptyList.of(name.name()), tags)
    metricIdentifier
  }

}
