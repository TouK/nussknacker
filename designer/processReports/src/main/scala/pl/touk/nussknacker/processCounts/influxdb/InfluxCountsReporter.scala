package pl.touk.nussknacker.processCounts.influxdb

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.processCounts._
import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.client.{NothingT, SttpBackend}

import java.time.Instant
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.higherKinds

/*
  Base reporter for counts
 */
class InfluxCountsReporter[F[_]](env: String, config: InfluxConfig, waitForClose: F[Unit] => Unit)(implicit backend: SttpBackend[F, Nothing, NothingT]) extends CountsReporter[F] with LazyLogging {

  val influxGenerator = new InfluxGenerator(config, env)

  private implicit val monadError: MonadError[F] = backend.responseMonad

  private val metricsConfig = config.metricsConfig.getOrElse(MetricsConfig())

  override def prepareRawCounts(processId: String, countsRequest: CountsRequest): F[String => Option[Long]] = (countsRequest match {
    case RangeCount(fromDate, toDate) => prepareRangeCounts(processId, fromDate, toDate)
    case ExecutionCount(pointInTime) => influxGenerator.queryBySingleDifference(processId, None, pointInTime, metricsConfig)
  }).map(_.get)

  override def close(): Unit = waitForClose(influxGenerator.close())

  private def prepareRangeCounts(processId: String, fromDate: Instant, toDate: Instant): F[Map[String, Long]] = {

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
          monadError.error(CannotFetchCountsError.restartsDetected(dates))
        //should not happen, unfortunately scalac cannot detect that all enum values were handled...
        case _ => monadError.error(new IllegalArgumentException(s"Unknown QueryMode ${config.queryMode} for ${restarts.size} restarts"))
      }
    }
  }

}

class InfluxCountsReporterCreator extends CountsReporterCreator {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  override def createReporter(env: String, config: Config)
                             (implicit backend: SttpBackend[Future, Nothing, NothingT]): CountsReporter[Future] = {
    //TODO: logger
    new InfluxCountsReporter(env, config.as[InfluxConfig](CountsReporterCreator.reporterCreatorConfigPath), Await.result(_, 10 seconds))
  }

}
