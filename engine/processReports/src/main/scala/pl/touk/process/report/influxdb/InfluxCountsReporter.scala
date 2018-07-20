package pl.touk.process.report.influxdb

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import pl.touk.process.report.{CannotFetchCountsError, CountsReporter}

import scala.concurrent.{ExecutionContext, Future}

class InfluxCountsReporter(env: String, config: InfluxReporterConfig) extends CountsReporter {

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  private val influxBaseReporter = new InfluxBaseCountsReporter(env, config)

  override def prepareRawCounts(processId: String, fromDate: LocalDateTime, toDate: LocalDateTime)(implicit ec: ExecutionContext): Future[String => Option[Long]] = {

    influxBaseReporter.detectRestarts(processId, fromDate, toDate).flatMap {
      case Nil => influxBaseReporter
        .fetchBaseProcessCounts(processId, fromDate, toDate).map(pbc => nodeId => pbc.getCountForNodeId(nodeId))
      case dates => Future.failed(CannotFetchCountsError(s"Counts unavailable, as process was restarted/deployed on " +
        s" following dates: ${dates.map(_.format(dateTimeFormatter)).mkString(", ")}"))
    }


  }
}
