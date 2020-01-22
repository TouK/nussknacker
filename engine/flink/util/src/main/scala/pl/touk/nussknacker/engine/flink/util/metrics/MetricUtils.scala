package pl.touk.nussknacker.engine.flink.util.metrics

import java.util.ServiceLoader

import cats.data.NonEmptyList
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.metrics.{Counter, Gauge, Histogram, Meter, MetricGroup}
import pl.touk.nussknacker.engine.flink.api.RuntimeContextLifecycle

trait UseNewMetrics

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

  private val useNewMetricsMode: Boolean = ServiceLoader.load(classOf[UseNewMetrics], runtimeContext.getUserCodeClassLoader).iterator().hasNext

  private def groupsWithName(nameParts: NonEmptyList[String], tags: Map[String, String]): (MetricGroup, String) = {
    if (useNewMetricsMode) {
      tagMode(nameParts, tags)
    } else {
      groupsWithNameForLegacyMode(nameParts, tags)

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
    val insertServiceName = insertTag("serviceName") _

    nameParts match {

      //RateMeterFunction, no tags here
      case l@NonEmptyList("source", _) => tagMode(l, Map.empty)
      //EventTimeDelayMeterFunction, no tags here
      case l@NonEmptyList("eventtimedelay", _) => tagMode(l, Map.empty)

      //EndRateMeterFunction, nodeId tag
      case l@NonEmptyList("end", _) => insertNodeId(l)
      case l@NonEmptyList("dead_end", _) => insertNodeId(l)

      //NodeCountMetricListener nodeId tag
      case l@NonEmptyList("nodeCount", _) =>insertNodeId(l)

      //GenericTimeMeasuringService
      case l@NonEmptyList("service", name :: "instantRate" :: Nil) => tagMode(NonEmptyList("serviceInstant",  tags("serviceName") :: name :: Nil), Map.empty)
      case l@NonEmptyList("service", name :: "histogram" :: Nil) => tagMode(NonEmptyList("serviceTimes", tags("serviceName") :: name :: Nil), Map.empty)

      case l@NonEmptyList("error", List("instantRate")) => tagMode(l, Map.empty)
      case l@NonEmptyList("error", List("instantRateByNode")) => insertNodeId(l)
        
      //we resort to default mode...
      case _ => tagMode(nameParts, tags)
    }
  }


}

trait WithMetrics extends RuntimeContextLifecycle {

  @transient protected var metricUtils : MetricUtils = _

  override def open(runtimeContext: RuntimeContext): Unit = {
    this.metricUtils = new MetricUtils(runtimeContext)
  }

}
