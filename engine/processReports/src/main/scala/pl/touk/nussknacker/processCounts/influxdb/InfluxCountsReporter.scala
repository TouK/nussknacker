package pl.touk.nussknacker.processCounts.influxdb

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.processCounts._
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

/*
  Base
 */
class InfluxCountsReporter(env: String, config: InfluxConfig)(implicit backend: SttpBackend[Future, Nothing, NothingT]) extends CountsReporter with LazyLogging {

  val influxGenerator = new InfluxGenerator(config, env)
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  private val metricsConfig = config.metricsConfig.getOrElse(MetricsConfig())

  override def prepareRawCounts(processId: String, countsRequest: CountsRequest)(implicit ec: ExecutionContext): Future[String => Option[Long]] = (countsRequest match {
    case RangeCount(fromDate, toDate) => prepareRangeCounts(processId, fromDate, toDate)
    case ExecutionCount(pointInTime) => influxGenerator.queryBySingleDifference(processId, None, pointInTime, metricsConfig)
  }).map(ProcessBaseCounts).map(_.getCountForNodeId)

  override def close(): Unit = influxGenerator.close()

  private def prepareRangeCounts(processId: String, fromDate: LocalDateTime, toDate: LocalDateTime)(implicit ec: ExecutionContext): Future[Map[String, Long]] = {

    influxGenerator.detectRestarts(processId, fromDate, toDate, metricsConfig).flatMap { restarts =>
      (restarts, config.queryMode) match {
        case (_, QueryMode.OnlySumOfDifferences) =>
          influxGenerator.queryBySumOfDifferences(processId, fromDate, toDate, metricsConfig)
        case (Nil, QueryMode.SumOfDifferencesForRestarts) =>
          influxGenerator.queryBySingleDifference(processId, Some(fromDate), toDate, metricsConfig)
        case (nonEmpty, QueryMode.SumOfDifferencesForRestarts) =>
          logger.debug(s"Restarts detected: ${nonEmpty.mkString(",")}, querying with differential")
          influxGenerator.queryBySumOfDifferences(processId, fromDate, toDate, metricsConfig)
        case (Nil, QueryMode.OnlySingleDifference) =>
          influxGenerator.queryBySingleDifference(processId, Some(fromDate), toDate, metricsConfig)
        case (dates, QueryMode.OnlySingleDifference) =>
          Future.failed(CannotFetchCountsError(s"Counts unavailable, as process was restarted/deployed on " +
          s" following dates: ${dates.map(_.format(dateTimeFormatter)).mkString(", ")}"))
      }
    }
  }

}

class InfluxCountsReporterCreator extends CountsReporterCreator {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  override def createReporter(env: String, config: Config): CountsReporter = {
    //TODO: logger
    implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())
    new InfluxCountsReporter(env, config.as[InfluxConfig](CountsReporterCreator.reporterCreatorConfigPath))
  }

}
