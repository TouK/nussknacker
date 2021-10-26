package pl.touk.nussknacker.processCounts.influxdb

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.processCounts._
import sttp.client.{NothingT, SttpBackend}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/*
  Base reporter for counts
 */
class InfluxCountsReporter(env: String, config: InfluxConfig)(implicit backend: SttpBackend[Future, Nothing, NothingT]) extends CountsReporter with LazyLogging {

  val influxGenerator = new InfluxGenerator(config, env)
  private val metricsConfig = config.metricsConfig.getOrElse(MetricsConfig())

  override def prepareRawCounts(processId: String, countsRequest: CountsRequest)(implicit ec: ExecutionContext): Future[String => Option[Long]] = (countsRequest match {
    case RangeCount(fromDate, toDate) => prepareRangeCounts(processId, fromDate, toDate)
    case ExecutionCount(pointInTime) => influxGenerator.queryBySingleDifference(processId, None, pointInTime, metricsConfig)
  }).map(_.get)

  override def close(): Unit = influxGenerator.close()

  private def prepareRangeCounts(processId: String, fromDate: Instant, toDate: Instant)(implicit ec: ExecutionContext): Future[Map[String, Long]] = {

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
          Future.failed(CannotFetchCountsError.restartsDetected(dates))
        //should not happen, unfortunately scalac cannot detect that all enum values were handled...
        case _ => Future.failed(new IllegalArgumentException(s"Unknown QueryMode ${config.queryMode} for ${restarts.size} restarts"))
      }
    }
  }

}

class InfluxCountsReporterCreator extends CountsReporterCreator {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  override def createReporter(env: String, config: Config)
                             (implicit backend: SttpBackend[Future, Nothing, NothingT]): CountsReporter = {
    //TODO: logger
    new InfluxCountsReporter(env, config.as[InfluxConfig](CountsReporterCreator.reporterCreatorConfigPath))
  }

}
