package pl.touk.nussknacker.processCounts.influxdb

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import sttp.client.{NothingT, SttpBackend}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import com.typesafe.config.Config
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.processCounts._

import scala.concurrent.{ExecutionContext, Future}

class InfluxCountsReporter(env: String, config: InfluxConfig)(implicit backend: SttpBackend[Future, Nothing, NothingT]) extends CountsReporter {

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  private val influxBaseReporter = new InfluxBaseCountsReporter(env, config)

  override def prepareRawCounts(processId: String, countsRequest: CountsRequest)(implicit ec: ExecutionContext): Future[String => Option[Long]] = countsRequest match {
    case RangeCount(fromDate, toDate) => prepareRangeCounts(processId, fromDate, toDate)
    case ExecutionCount(pointInTime) => queryInflux(processId, None, pointInTime)
  }

  private def prepareRangeCounts(processId: String, fromDate: LocalDateTime, toDate: LocalDateTime)(implicit ec: ExecutionContext): Future[String => Option[Long]] = {
    influxBaseReporter.detectRestarts(processId, fromDate, toDate).flatMap {
      case Nil => queryInflux(processId, Some(fromDate), toDate)
      case dates => Future.failed(CannotFetchCountsError(s"Counts unavailable, as process was restarted/deployed on " +
        s" following dates: ${dates.map(_.format(dateTimeFormatter)).mkString(", ")}"))
    }

  }

  private def queryInflux(processId: String, fromDate: Option[LocalDateTime], toDate: LocalDateTime)(implicit ec: ExecutionContext): Future[String => Option[Long]] = {
    influxBaseReporter.fetchBaseProcessCounts(processId, fromDate, toDate).map(pbc => nodeId => pbc.getCountForNodeId(nodeId))
  }

  override def close(): Unit = influxBaseReporter.influxGenerator.close()

}

class InfluxCountsReporterCreator extends CountsReporterCreator {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._


  override def createReporter(env: String, config: Config): CountsReporter = {
    //TODO: logger
    implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())
    new InfluxCountsReporter(env, config.as[InfluxConfig](CountsReporterCreator.reporterCreatorConfigPath))
  }

}
