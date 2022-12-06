package pl.touk.nussknacker.ui.statistics

import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import scala.collection.immutable.ListMap
import scala.util.Random

case class UsageStatisticsHtmlSnippet(value: String)

object UsageStatisticsHtmlSnippet {

  def prepareWhenEnabledReporting(config: UsageStatisticsReportsConfig): Option[UsageStatisticsHtmlSnippet] = {
    if (config.enabled) {
      val queryParams = prepareQueryParams(config)
      val url = prepareUrl(queryParams)
      Some(UsageStatisticsHtmlSnippet(s"""<img src="$url" alt="anonymous usage reporting" referrerpolicy="origin" hidden />"""))
    } else {
      None
    }
  }

  private def prepareQueryParams(config: UsageStatisticsReportsConfig) = {
    ListMap(
      "fingerprint" -> config.fingerprint.getOrElse(randomFingerprint),
      "version" -> BuildInfo.version)
  }

  private[statistics] def prepareUrl(queryParams: ListMap[String, String]) = {
    val queryParamsPart = queryParams.toList.map { case (k, v) => s"$k=${URLEncoder.encode(v, StandardCharsets.UTF_8)}" }.mkString("&")
    s"https://stats.nussknacker.io/?$queryParamsPart"
  }

  private lazy val randomFingerprint = s"gen-${Random.alphanumeric.take(10).mkString}"

}
