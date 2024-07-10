package pl.touk.nussknacker.ui.statistics

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.util.IterableExtensions.Chunked

import java.net.{URI, URL, URLEncoder}
import java.nio.charset.StandardCharsets
import scala.util.{Failure, Success, Try}

sealed trait Statistics {
  def prepareURLs(cfg: StatisticUrlConfig): Either[StatisticError, List[URL]]
}

object Statistics extends LazyLogging {

  final class NonEmpty(fingerprint: Fingerprint, correlationId: CorrelationId, rawStatistics: Map[String, String])
      extends Statistics
      with LazyLogging {

    private lazy val queryParamsForEveryURL = List(
      encodeQueryParam(NuFingerprint.name     -> fingerprint.value),
      encodeQueryParam(CorrelationIdStat.name -> correlationId.value)
    )

    override def prepareURLs(cfg: StatisticUrlConfig): Either[StatisticError, List[URL]] =
      rawStatistics.toList
        // Sorting for purpose of easier testing
        .sortBy(_._1)
        .map(encodeQueryParam)
        .groupByMaxChunkSize(cfg.urlBytesSizeLimit)
        .flatMap(queryParams => prepareUrlString(queryParams, cfg))
        .traverse(toURL)

    private def encodeQueryParam(entry: (String, String)): String =
      s"${URLEncoder.encode(entry._1, StandardCharsets.UTF_8)}=${URLEncoder.encode(entry._2, StandardCharsets.UTF_8)}"

    private def prepareUrlString(queryParams: Iterable[String], cfg: StatisticUrlConfig): Option[String] = {
      if (queryParams.nonEmpty) {
        val queryParamsWithFingerprint = queryParams ++ queryParamsForEveryURL
        Some(queryParamsWithFingerprint.mkString(cfg.nuStatsUrl, cfg.queryParamsSeparator, cfg.emptyString))
      } else {
        None
      }
    }

  }

  final object Empty extends Statistics {
    override def prepareURLs(cfg: StatisticUrlConfig): Either[StatisticError, List[URL]] = Right(Nil)
  }

  private[statistics] def toURL(urlString: String): Either[StatisticError, URL] =
    Try(new URI(urlString).toURL) match {
      case Failure(ex) => {
        logger.warn(s"Exception occurred while creating URL from string: [$urlString]", ex)
        Left(CannotGenerateStatisticsError)
      }
      case Success(value) => Right(value)
    }

}
