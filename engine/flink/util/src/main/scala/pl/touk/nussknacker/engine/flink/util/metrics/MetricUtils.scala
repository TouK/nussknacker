package pl.touk.nussknacker.engine.flink.util.metrics

import cats.data.NonEmptyList
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.metrics.{Counter, Gauge, Histogram, Meter, MetricGroup}
import pl.touk.nussknacker.engine.flink.api.{NkGlobalParameters, RuntimeContextLifecycle}

class MetricUtils(runtimeContext: RuntimeContext) {

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

  def histogram(nameParts: NonEmptyList[String], tags: Map[String, String], histogram: Histogram): Histogram = {
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

trait WithMetrics extends RuntimeContextLifecycle {

  @transient protected var metricUtils : MetricUtils = _

  override def open(runtimeContext: RuntimeContext): Unit = {
    this.metricUtils = new MetricUtils(runtimeContext)
  }

}
