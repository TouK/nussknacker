package pl.touk.nussknacker.engine.process.compiler

import cats.data.NonEmptyList
import com.codahale.metrics
import com.codahale.metrics.SlidingTimeWindowReservoir
import org.apache.flink
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper
import org.apache.flink.metrics.MetricGroup
import pl.touk.nussknacker.engine.flink.api.NkGlobalParameters
import pl.touk.nussknacker.engine.util.metrics._

import java.util.concurrent.TimeUnit

class FlinkMetricsProviderForScenario(runtimeContext: RuntimeContext) extends BaseMetricsProviderForScenario {

  override def registerGauge[T](identifier: MetricIdentifier, value: Gauge[T]): Unit =
    gauge[T, flink.metrics.Gauge[T]](identifier.name, identifier.tags, () => value.getValue)

  override def counter(identifier: MetricIdentifier): Counter = {
    val counterInstance = counter(identifier.name, identifier.tags)
    counterInstance.inc _
  }

  override def histogram(identifier: MetricIdentifier, instantTimerWindowInSeconds: Long): Histogram = {
    val histogramInstance = new DropwizardHistogramWrapper(
      new metrics.Histogram(new SlidingTimeWindowReservoir(instantTimerWindowInSeconds, TimeUnit.SECONDS))
    )
    histogram(identifier.name, identifier.tags, histogramInstance).update _
  }

  def counter(nameParts: NonEmptyList[String], tags: Map[String, String]): flink.metrics.Counter = {
    val (group, name) = groupsWithName(nameParts, tags)
    group.counter(name)
  }

  def gauge[T, Y <: flink.metrics.Gauge[T]](nameParts: NonEmptyList[String], tags: Map[String, String], gauge: Y): Y = {
    val (group, name) = groupsWithName(nameParts, tags)
    group.gauge[T, Y](name, gauge)
  }

  def histogram(
      nameParts: NonEmptyList[String],
      tags: Map[String, String],
      histogram: flink.metrics.Histogram
  ): flink.metrics.Histogram = {
    val (group, name) = groupsWithName(nameParts, tags)
    group.histogram(name, histogram)
  }

  override def remove(metricIdentifier: MetricIdentifier): Unit = {
    ??? // Shouldn't be needed because Flink jobs are recreated "from scratch" and no cleanup of metrics during cancel is needed
  }

  private def groupsWithName(nameParts: NonEmptyList[String], tags: Map[String, String]): (MetricGroup, String) = {
    val namespaceTags = extractTags(NkGlobalParameters.fromMap(runtimeContext.getGlobalJobParameters))
    tagMode(nameParts, tags ++ namespaceTags)
  }

  private def tagMode(nameParts: NonEmptyList[String], tags: Map[String, String]): (MetricGroup, String) = {
    val lastName = nameParts.last
    // all but last
    val metricNameParts    = nameParts.init
    val groupWithNameParts = metricNameParts.foldLeft[MetricGroup](runtimeContext.getMetricGroup)(_.addGroup(_))

    val finalGroup = tags.toList.sortBy(_._1).foldLeft[MetricGroup](groupWithNameParts) {
      case (group, (tag, tagValue)) => group.addGroup(tag, tagValue)
    }
    (finalGroup, lastName)
  }

  private def extractTags(nkGlobalParameters: Option[NkGlobalParameters]): Map[String, String] = {
    nkGlobalParameters.map(_.namespaceParameters) match {
      case Some(Some(params)) => params.tags
      case _                  => Map()
    }
  }

}
