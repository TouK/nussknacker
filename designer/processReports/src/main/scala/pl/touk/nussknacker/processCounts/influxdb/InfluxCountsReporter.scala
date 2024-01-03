package pl.touk.nussknacker.processCounts.influxdb

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.processCounts._
import sttp.client3.SttpBackend
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.time.Instant
import scala.concurrent.Future
import scala.language.higherKinds

/*
  Base reporter for counts
 */
class InfluxCountsReporter[F[_]](env: String, config: InfluxConfig)(implicit backend: SttpBackend[F, Any])
    extends CountsReporter[F]
    with LazyLogging {

  private val influxGenerator = new InfluxGenerator(config, env)

  private implicit val monadError: MonadError[F] = backend.responseMonad

  private val metricsConfig = config.metricsConfig.getOrElse(MetricsConfig())

  override def prepareRawCounts(processName: ProcessName, countsRequest: CountsRequest): F[String => Option[Long]] =
    (countsRequest match {
      case RangeCount(fromDate, toDate) => prepareRangeCounts(processName, fromDate, toDate)
      case ExecutionCount(pointInTime) =>
        influxGenerator.queryBySingleDifference(processName, None, pointInTime, metricsConfig)
    }).map(_.get)

  override def close(): Unit = {}

  private def prepareRangeCounts(processName: ProcessName, fromDate: Instant, toDate: Instant): F[Map[String, Long]] = {

    influxGenerator.detectRestarts(processName, fromDate, toDate, metricsConfig).flatMap { restarts =>
      (restarts, config.queryMode) match {
        case (_, QueryMode.OnlySumOfDifferences) =>
          influxGenerator.queryBySumOfDifferences(processName, fromDate, toDate, metricsConfig)
        case (Nil, QueryMode.SumOfDifferencesForRestarts) =>
          influxGenerator.queryBySingleDifference(processName, Some(fromDate), toDate, metricsConfig)
        case (nonEmpty, QueryMode.SumOfDifferencesForRestarts) =>
          logger.debug(s"Restarts detected: ${nonEmpty.mkString(",")}, querying with differential")
          influxGenerator.queryBySumOfDifferences(processName, fromDate, toDate, metricsConfig)
        case (Nil, QueryMode.OnlySingleDifference) =>
          influxGenerator.queryBySingleDifference(processName, Some(fromDate), toDate, metricsConfig)
        case (dates, QueryMode.OnlySingleDifference) =>
          monadError.error(CannotFetchCountsError.restartsDetected(dates))
        // should not happen, unfortunately scalac cannot detect that all enum values were handled...
        case _ =>
          monadError
            .error(new IllegalArgumentException(s"Unknown QueryMode ${config.queryMode} for ${restarts.size} restarts"))
      }
    }
  }

}

class InfluxCountsReporterCreator extends CountsReporterCreator {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  override def createReporter(env: String, config: Config)(
      implicit backend: SttpBackend[Future, Any]
  ): CountsReporter[Future] = {
    // TODO: logger
    new InfluxCountsReporter(env, config.as[InfluxConfig](CountsReporterCreator.reporterCreatorConfigPath))
  }

}
