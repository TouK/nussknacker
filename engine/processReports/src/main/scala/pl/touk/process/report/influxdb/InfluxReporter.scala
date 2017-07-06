package pl.touk.process.report.influxdb

import java.time.LocalDateTime

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.util.{Failure, Success}

class InfluxReporter(env: String, config: InfluxReporterConfig) extends LazyLogging {
  import scala.concurrent.ExecutionContext.Implicits.global

  def fetchBaseProcessCounts(processId: String, dateFrom: LocalDateTime, dateTo: LocalDateTime): Future[ProcessBaseCounts] = {
    //TODO this inlfuxUrl can be fetched using grafana API
    val influxGenerator = new InfluxGenerator(config.influxUrl, config.user, config.password, config.database, env)
    val reportData = for {
      allCount <- influxGenerator.query(processId, "source", dateFrom, dateTo).map(_.getOrElse("count", 0L))
      //TODO: do we need these counts now?
      endCount <- influxGenerator.query(processId, "end.count", dateFrom, dateTo)
      deadEndCount <- influxGenerator.query(processId, "dead_end.count", dateFrom, dateTo)
      nodes <- influxGenerator.query(processId, "nodeCount", dateFrom, dateTo)
    } yield ProcessBaseCounts(all = allCount, ends = endCount, deadEnds = deadEndCount, nodes = nodes)
    reportData.onComplete {
      case Success(_) =>
        influxGenerator.close()
      case Failure(ex) =>
        logger.error("Failed to generate", ex)
        influxGenerator.close()
    }
    reportData
  }

}

case class ProcessBaseCounts(all: Long, ends: Map[String, Long], deadEnds: Map[String, Long], nodes: Map[String, Long]) {

  //node ids in influx are different than original ones, i.e influx converts spaces and dots to dashes '-'
  //that's wy we need reverse transformation
  def mapToOriginalNodeIds(allNodeIds: List[String]): ProcessBaseCounts = {
    copy(ends = mapToOriginalNodeIds(allNodeIds, ends))
      .copy(deadEnds = mapToOriginalNodeIds(allNodeIds, deadEnds))
        .copy(nodes = mapToOriginalNodeIds(allNodeIds, nodes))
  }

  private def mapToOriginalNodeIds(allNodeIds: List[String], counts: Map[String, Long]): Map[String, Long] = {
    allNodeIds.flatMap { nodeId =>
      counts.get(mapSpecialCharactersInfluxStyle(nodeId)).map { nodeCount =>
        nodeId -> nodeCount
      }
    }.toMap
  }

  private def mapSpecialCharactersInfluxStyle(nodeId: String) = {
    nodeId.replaceAll("\\.+", "\\-")
      .replaceAll("\\ +", "\\-")
  }
}

case class InfluxReporterConfig(influxUrl: String, user: String, password: String, database: String = "esp")