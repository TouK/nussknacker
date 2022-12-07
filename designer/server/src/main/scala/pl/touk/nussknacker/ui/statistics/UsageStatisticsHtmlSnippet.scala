package pl.touk.nussknacker.ui.statistics

import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import scala.collection.immutable.ListMap
import scala.util.Random

case class UsageStatisticsHtmlSnippet(value: String)

object UsageStatisticsHtmlSnippet {

  def prepareWhenEnabledReporting(config: UsageStatisticsReportsConfig,
                                  processingTypeStatistics: ProcessingTypeDataProvider[ProcessingTypeUsageStatistics]): Option[UsageStatisticsHtmlSnippet] = {
    if (config.enabled) {
      val queryParams = prepareQueryParams(config, processingTypeStatistics.all)
      val url = prepareUrl(queryParams)
      Some(UsageStatisticsHtmlSnippet(s"""<img src="$url" alt="anonymous usage reporting" referrerpolicy="origin" hidden />"""))
    } else {
      None
    }
  }

  private def prepareQueryParams(config: UsageStatisticsReportsConfig,
                                 processingTypeStatisticsMap: Map[ProcessingType, ProcessingTypeUsageStatistics]): ListMap[ProcessingType, ProcessingType] = {
    ListMap(
      "fingerprint" -> config.fingerprint.getOrElse(randomFingerprint),
      "version" -> BuildInfo.version,
      "deploymentManagerTypes" -> "TODO",
      "processingModes" -> "TODO"
    )
  }

  private[statistics] def prepareUrl(queryParams: ListMap[String, String]) = {
    val queryParamsPart = queryParams.toList.map { case (k, v) => s"$k=${URLEncoder.encode(v, StandardCharsets.UTF_8)}" }.mkString("&")
    s"https://stats.nussknacker.io/?$queryParamsPart"
  }

  private lazy val randomFingerprint = s"gen-${Random.alphanumeric.take(10).mkString}"

}
