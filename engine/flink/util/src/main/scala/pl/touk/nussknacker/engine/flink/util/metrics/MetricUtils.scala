package pl.touk.nussknacker.engine.flink.util.metrics

import cats.data.NonEmptyList
import com.codahale.metrics
import com.codahale.metrics.SlidingTimeWindowReservoir
import org.apache.flink
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper
import org.apache.flink.metrics._
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.runtimecontext.{EngineRuntimeContext, EngineRuntimeContextLifecycle}
import pl.touk.nussknacker.engine.flink.api.NkGlobalParameters
import pl.touk.nussknacker.engine.util.metrics.{MetricIdentifier, MetricsProvider}
import pl.touk.nussknacker.engine.util.service.EspTimer

import java.util.concurrent.TimeUnit

class MetricUtils(runtimeContext: RuntimeContext) extends MetricsProvider {

  override def espTimer(identifier: MetricIdentifier, instantTimerWindowInSeconds: Long): EspTimer = {
    val meter = gauge[Double, InstantRateMeter](identifier.name :+ EspTimer.instantRateSuffix, identifier.tags, new InstantRateMeter)
    val registered = histogram(identifier.withNameSuffix(EspTimer.histogramSuffix), instantTimerWindowInSeconds)
    EspTimer(meter, registered)
  }

  override def registerGauge[T](identifier: MetricIdentifier, value: () => T): Unit =
    gauge[T, Gauge[T]](identifier.name, identifier.tags, () => value())

  override def counter(identifier: MetricIdentifier): Long => Unit = {
    val counterInstance = counter(identifier.name, identifier.tags)
    counterInstance.inc
  }

  override def histogram(identifier: MetricIdentifier, instantTimerWindowInSeconds: Long): Long => Unit = {
    val histogramInstance = new DropwizardHistogramWrapper(new metrics.Histogram(new SlidingTimeWindowReservoir(instantTimerWindowInSeconds, TimeUnit.SECONDS)))
    histogram(identifier.name, identifier.tags, histogramInstance).update _
  }


  def counter(nameParts: NonEmptyList[String], tags: Map[String, String]): Counter = {
    val (group, name) = groupsWithName(nameParts, tags)
    group.counter(name)
  }

  def gauge[T, Y<: Gauge[T]](nameParts: NonEmptyList[String], tags: Map[String, String], gauge: Y): Y = {
    val (group, name) = groupsWithName(nameParts, tags)
    group.gauge[T, Y](name, gauge)
  }

  //currently not used - maybe we should? :)
  def meter(nameParts: NonEmptyList[String], tags: Map[String, String], meter: Meter): Meter = {
    val (group, name) = groupsWithName(nameParts, tags)
    group.meter(name, meter)
  }

  def histogram(nameParts: NonEmptyList[String], tags: Map[String, String], histogram: flink.metrics.Histogram): flink.metrics.Histogram = {
    val (group, name) = groupsWithName(nameParts, tags)
    group.histogram(name, histogram)
  }

  private def groupsWithName(nameParts: NonEmptyList[String], tags: Map[String, String]): (MetricGroup, String) = {
    val namespaceTags = extractTags(NkGlobalParameters.readFromContext(runtimeContext.getExecutionConfig))
    tagMode(nameParts, tags ++ namespaceTags)
  }

  private def tagMode(nameParts: NonEmptyList[String], tags: Map[String, String]): (MetricGroup, String) = {
    val lastName = nameParts.last
    //all but last
    val metricNameParts = nameParts.init
    val groupWithNameParts = metricNameParts.foldLeft[MetricGroup](runtimeContext.getMetricGroup)(_.addGroup(_))

    val finalGroup = tags.toList.sortBy(_._1).foldLeft[MetricGroup](groupWithNameParts) {
      case (group, (tag, tagValue)) => group.addGroup(tag, tagValue)
    }
    (finalGroup, lastName)
  }

  private def extractTags(nkGlobalParameters: Option[NkGlobalParameters]): Map[String, String] = {
    nkGlobalParameters.map(_.namingParameters) match {
      case Some(Some(params)) => params.tags
      case _ => Map()
    }
  }

}

trait WithMetrics extends EngineRuntimeContextLifecycle {

  @transient protected var metricsProvider : MetricsProvider = _

  override def open(jobData: JobData, context: EngineRuntimeContext): Unit = {
    this.metricsProvider = context.metricsProvider
  }

}
