package pl.touk.process.report.influxdb

import java.time.LocalDateTime

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.util.{Failure, Success}

class InfluxReporter(env: String, config: InfluxReporterConfig) extends LazyLogging {
  import scala.concurrent.ExecutionContext.Implicits.global

  def fetchBaseProcessCounts(processId: String, dateFrom: LocalDateTime, dateTo: LocalDateTime): Future[ProcessBaseCounts] = {
    //todo tego influxUrl mozna samemu dociagac korzystajac z api grafany
    val influxGenerator = new InfluxGenerator(config.influxUrl, config.user, config.password, config.database, env)
    val reportData = for {
      allCount <- influxGenerator.query(processId, "source", dateFrom, dateTo).map(_.getOrElse("count", 0L))
      //TODO: czy potrzebujemy teraz tych countow??
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

  //idiki nodow w metrykach z influxa sa inne niz te originalne, np influx zamienia spacje i kropki na '-'
  //dlatego musimy wykonac transformacje odwrotna
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