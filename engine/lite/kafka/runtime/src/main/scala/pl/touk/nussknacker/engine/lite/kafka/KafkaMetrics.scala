package pl.touk.nussknacker.engine.lite.kafka

import cats.data.NonEmptyList
import org.apache.kafka.common.{Metric, MetricName}
import pl.touk.nussknacker.engine.util.metrics.{Gauge, MetricIdentifier, MetricsProviderForScenario}

import scala.jdk.CollectionConverters.mapAsScalaMapConverter

//We have to pass taskId, as we will need
private[kafka] class KafkaMetrics(taskId: String, metricsProvider: MetricsProviderForScenario) {

  def registerMetrics(metrics: java.util.Map[MetricName, _ <: Metric]): Unit = {
    metrics.forEach { case (name, metric) =>
      val tags = name.tags().asScala.toMap + ("taskId" -> taskId) + ("kafkaGroup" -> name.group())
      metricsProvider.registerGauge[AnyRef](MetricIdentifier(NonEmptyList.of(name.name()), tags), new Gauge[AnyRef] {
        override def getValue: AnyRef = metric.metricValue()
      })
    }

  }
}
