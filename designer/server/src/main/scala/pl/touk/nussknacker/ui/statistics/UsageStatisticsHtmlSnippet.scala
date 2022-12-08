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

  private[statistics] def prepareQueryParams(config: UsageStatisticsReportsConfig,
                                             processingTypeStatisticsMap: Map[ProcessingType, ProcessingTypeUsageStatistics]): ListMap[String, String] = {
    val deploymentManagerTypes = processingTypeStatisticsMap.values.map(_.deploymentManagerType)
    val dmParams = prepareValuesParams(deploymentManagerTypes, "dm")
    val processingModes = processingTypeStatisticsMap.values.collect {
      case ProcessingTypeUsageStatistics(_, Some(mode)) => mode
      case ProcessingTypeUsageStatistics(deploymentManagerType, _) if deploymentManagerType.toLowerCase.contains("streaming") => "streaming"
    }
    val mParams = prepareValuesParams(processingModes, "m")
    ListMap(
      // We filter out blank fingerprints because when smb uses docker-compose, and forwards env variables USAGE_REPORTS_FINGERPRINT
      // from system and the variable doesn't exist, there is no way to skip variable - it can be only set to empty
      "fingerprint" -> config.fingerprint.filterNot(_.isBlank).getOrElse(randomFingerprint),
      "version" -> BuildInfo.version
    ) ++ dmParams ++ mParams
  }

  private def prepareValuesParams(values: Iterable[ProcessingType], metricCategoryKeyPart: String) = {
    val countsParams = values.groupBy(identity).mapValues(_.size).map {
      case (value, count) =>
        s"${metricCategoryKeyPart}_$value" -> count.toString
    }.toList.sortBy(_._1)
    val singleParamOpt = Option(values.toSet.toList).collect {
      case single :: Nil => s"single_$metricCategoryKeyPart" -> single
    }
    ListMap(countsParams ++ singleParamOpt: _*)
  }

  private[statistics] def prepareUrl(queryParams: ListMap[String, String]) = {
    queryParams.toList.map {
      case (k, v) => s"${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
    }.mkString("https://stats.nussknacker.io/?", "&", "")
  }

  private lazy val randomFingerprint = s"gen-${Random.alphanumeric.take(10).mkString}"

}
