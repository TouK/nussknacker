package pl.touk.nussknacker.ui.statistics

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.util.IterableExtensions.Chunked

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class StatisticsUrls(cfg: StatisticUrlConfig) extends LazyLogging {

  def prepare(fingerprint: Fingerprint, correlationId: String, rawStatistics: Map[String, String]): List[String] =
    rawStatistics.toList
      // Sorting for purpose of easier testing
      .sortBy(_._1)
      .map(encodeQueryParam)
      .groupByMaxChunkSize(cfg.urlBytesSizeLimit)
      .flatMap(queryParams => prepareUrlString(queryParams, queryParamsForEveryURL(fingerprint, correlationId)))

  private def encodeQueryParam(entry: (String, String)): String =
    s"${URLEncoder.encode(entry._1, StandardCharsets.UTF_8)}=${URLEncoder.encode(entry._2, StandardCharsets.UTF_8)}"

  private def prepareUrlString(queryParams: Iterable[String], paramsForEveryURL: Iterable[String]): Option[String] = {
    if (queryParams.nonEmpty) {
      val queryParamsWithFingerprint = queryParams ++ paramsForEveryURL
      Some(queryParamsWithFingerprint.mkString(cfg.nuStatsUrl, cfg.queryParamsSeparator, cfg.emptyString))
    } else {
      None
    }
  }

  private def queryParamsForEveryURL(fingerprint: Fingerprint, correlationId: String): List[String] = List(
    encodeQueryParam(NuFingerprint.name -> fingerprint.value),
    encodeQueryParam(CorrelationId.name -> correlationId)
  )

}
