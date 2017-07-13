package pl.touk.process.report.influxdb

import java.time.LocalDateTime

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future

class InfluxReporter(env: String, config: InfluxReporterConfig) extends LazyLogging {
  import scala.concurrent.ExecutionContext.Implicits.global

  //TODO this inlfuxUrl can be fetched using grafana API
  val influxGenerator = new InfluxGenerator(config.influxUrl, config.user, config.password, config.database, env)

  def fetchBaseProcessCounts(processId: String, dateFrom: LocalDateTime, dateTo: LocalDateTime): Future[ProcessBaseCounts] = {

    val reportData = for {
      allCount <- influxGenerator.query(processId, "source", dateFrom, dateTo).map(_.getOrElse("count", 0L))
      nodes <- influxGenerator.query(processId, "nodeCount", dateFrom, dateTo)
    } yield ProcessBaseCounts(all = allCount, nodes = nodes)
    reportData.onFailure {
      case ex => logger.error("Failed to generate", ex)
    }
    reportData
  }

  def detectRestarts(processName: String, dateFrom: LocalDateTime, dateTo: LocalDateTime) : Future[List[LocalDateTime]]
    = influxGenerator.detectRestarts(processName, dateFrom, dateTo)

}

case class ProcessBaseCounts(all: Long, nodes: Map[String, Long]) {

  //node ids in influx are different than original ones, i.e influx converts spaces and dots to dashes '-'
  //that's wy we need reverse transformation
  def getCountForNodeId(nodeId: String) : Option[Long] = {
    nodes.get(mapSpecialCharactersInfluxStyle(nodeId))
  }

  private def mapSpecialCharactersInfluxStyle(nodeId: String) = {
    nodeId.replaceAll("\\.+", "\\-")
      .replaceAll("\\ +", "\\-")
  }
}

case class InfluxReporterConfig(influxUrl: String, user: String, password: String, database: String = "esp")