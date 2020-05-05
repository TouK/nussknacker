package pl.touk.nussknacker.engine.flink.util.metrics

import cats.data.NonEmptyList
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.metrics.{Counter, Gauge, Histogram, Meter, MetricGroup}
import pl.touk.nussknacker.engine.flink.api.{NkGlobalParameters, RuntimeContextLifecycle}

/*
  IMPORTANT: PLEASE keep Metrics.md up to date

  Handling Flink metrics is a bit tricky. For long time we parsed tags directly in Influx, via graphite plugin
  This is complex and error prone, so we'd like to switch to passing flink metric variables as tags via native influx API
  Unfortunately, current Flink Influx report doesn't allow for elastic configuration, so for now
  we translate by default to `old` way in groupsWithNameForLegacyMode
 */
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

  private val useLegacyMetricsMode: Boolean =
    NkGlobalParameters.readFromContext(runtimeContext.getExecutionConfig).flatMap(_.configParameters.flatMap(_.useLegacyMetrics)).getOrElse(false)

  private def groupsWithName(nameParts: NonEmptyList[String], tags: Map[String, String]): (MetricGroup, String) = {
    if (useLegacyMetricsMode) {
      groupsWithNameForLegacyMode(nameParts, tags)
    } else {
      val namespaceTags = extractTags(NkGlobalParameters.readFromContext(runtimeContext.getExecutionConfig))
      tagMode(nameParts, tags ++ namespaceTags)
    }
  }

  private def tagMode(nameParts: NonEmptyList[String], tags: Map[String, String]): (MetricGroup, String) = {
    val lastName = nameParts.last
    //all but last
    val metricNameParts = nameParts.init
    val groupWithNameParts = metricNameParts.foldLeft(runtimeContext.getMetricGroup)(_.addGroup(_))

    val finalGroup = tags.toList.sortBy(_._1).foldLeft(groupWithNameParts) {
      case (group, (tag, tagValue)) => group.addGroup(tag, tagValue)
    }
    (finalGroup, lastName)
  }

  private def groupsWithNameForLegacyMode(nameParts: NonEmptyList[String], tags: Map[String, String]): (MetricGroup, String) = {
    def insertTag(tagId: String)(nameParts: NonEmptyList[String]): (MetricGroup, String)
      = tagMode(NonEmptyList(nameParts.head, tags(tagId)::nameParts.tail), Map.empty)
    val insertNodeId = insertTag("nodeId") _

    nameParts match {

      //RateMeterFunction, nodeId tag
      case l@NonEmptyList("source", _) => insertNodeId(l)
      //EventTimeDelayMeterFunction, nodeId tag
      case l@NonEmptyList("eventtimedelay", _) => insertNodeId(l)

      //EndRateMeterFunction, nodeId tag
      case l@NonEmptyList("end", _) => insertNodeId(l)
      case l@NonEmptyList("dead_end", _) => insertNodeId(l)

      //NodeCountMetricListener nodeId tag
      case l@NonEmptyList("nodeCount", _) =>insertNodeId(l)

      //GenericTimeMeasuringService
      case l@NonEmptyList("service", name :: "instantRate" :: Nil) => tagMode(NonEmptyList("serviceInstant",  tags("serviceName") :: name :: Nil), Map.empty)
      case l@NonEmptyList("service", name :: "histogram" :: Nil) => tagMode(NonEmptyList("serviceTimes", tags("serviceName") :: name :: Nil), Map.empty)

      case l@NonEmptyList("error", "instantRate" :: "instantRate" :: Nil) => tagMode(l, Map.empty)
      case l@NonEmptyList("error", "instantRateByNode" :: "instantRate" :: Nil) => insertNodeId(l)

      case l@NonEmptyList("error", "instantRate" :: "count" :: Nil) => tagMode(l, Map.empty)
      case l@NonEmptyList("error", "instantRateByNode" :: "count" :: Nil) => insertNodeId(l)
        
      //we resort to default mode...
      case _ => tagMode(nameParts, tags)
    }
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