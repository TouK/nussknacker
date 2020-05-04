package pl.touk.nussknacker.processCounts.influxdb

import java.time.LocalDateTime

import sttp.client.{NothingT, SttpBackend}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future

private[influxdb] class InfluxBaseCountsReporter(env: String, config: InfluxConfig)(implicit backend: SttpBackend[Future, Nothing, NothingT]) extends LazyLogging {
  import scala.concurrent.ExecutionContext.Implicits.global

  val influxGenerator = new InfluxGenerator(config, env)

  def fetchBaseProcessCounts(processId: String, dateFrom: Option[LocalDateTime], dateTo: LocalDateTime): Future[ProcessBaseCounts] = {

    val reportData = for {
      nodes <- influxGenerator.query(processId, dateFrom, dateTo, config.metricsConfig.getOrElse(MetricsConfig()))
    } yield ProcessBaseCounts(nodes = nodes)
    reportData.failed.foreach {
      ex => logger.error("Failed to generate", ex)
    }
    reportData
  }

  def detectRestarts(processName: String, dateFrom: LocalDateTime, dateTo: LocalDateTime) : Future[List[LocalDateTime]]
    = influxGenerator.detectRestarts(processName, dateFrom, dateTo, config.metricsConfig.getOrElse(MetricsConfig()))

}

case class MetricsConfig(sourceCountMetric: String = "source_count",
                         nodeCountMetric: String = "nodeCount",
                         nodeIdTag: String = "nodeId",
                         slotTag: String = "slot",
                         processTag: String = "process",
                         countField: String = "count",
                         envTag: String = "env")


case class ProcessBaseCounts(nodes: Map[String, Long]) {

  //node ids in influx are different than original ones, i.e influx converts spaces and dots to dashes '-'
  //that's wy we need reverse transformation
  def getCountForNodeId(nodeId: String) : Option[Long] = {
    nodes.get(mapSpecialCharactersInfluxStyleNewVersions(nodeId))
      .orElse(nodes.get(mapSpecialCharactersInfluxStyleOldVersions(nodeId)))
  }

  //works for influx in version 1.3.7
  private def mapSpecialCharactersInfluxStyleNewVersions(nodeId: String) = {
    nodeId.replaceAll("\\.+", "\\-")
  }

  //works for influx in version 0.10.0-1
  private def mapSpecialCharactersInfluxStyleOldVersions(nodeId: String) = {
    nodeId.replaceAll("\\.+", "\\-")
      .replaceAll("\\ +", "\\-")
  }
}

