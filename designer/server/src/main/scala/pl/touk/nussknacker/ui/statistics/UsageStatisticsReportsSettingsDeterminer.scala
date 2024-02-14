package pl.touk.nussknacker.ui.statistics

import io.circe.generic.JsonCodec
import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettingsDeterminer._

import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import scala.collection.immutable.ListMap
import scala.util.{Random, Try}

object UsageStatisticsReportsSettingsDeterminer {

  private val nuFingerprintFileName = "nussknacker.fingerprint"

  private val knownDeploymentManagerTypes = Set("flinkStreaming", "lite-k8s", "lite-embedded")

  private val streamingProcessingMode = "streaming"

  private val knownProcessingModes = Set(streamingProcessingMode, "request-response")

  // We aggregate custom deployment managers and processing modes as a "custom" to avoid leaking of internal, confidential data
  private val aggregateForCustomValues = "custom"

  def apply(
      config: UsageStatisticsReportsConfig,
      processingTypeStatistics: ProcessingTypeDataProvider[ProcessingTypeUsageStatistics, _]
  ): UsageStatisticsReportsSettingsDeterminer = {
    UsageStatisticsReportsSettingsDeterminer(config, processingTypeStatistics.all(_))
  }

  def apply(
      config: UsageStatisticsReportsConfig,
      processingTypeStatistics: LoggedUser => Map[ProcessingType, ProcessingTypeUsageStatistics]
  ): UsageStatisticsReportsSettingsDeterminer = {
    val fingerprintFile = new File(
      Try(Option(System.getProperty("java.io.tmpdir"))).toOption.flatten.getOrElse("/tmp"),
      nuFingerprintFileName
    )
    new UsageStatisticsReportsSettingsDeterminer(config, processingTypeStatistics, fingerprintFile)
  }

  private[statistics] def prepareUrl(queryParams: ListMap[String, String]) = {
    queryParams.toList
      .map { case (k, v) =>
        s"${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
      }
      .mkString("https://stats.nussknacker.io/?", "&", "")
  }

}

class UsageStatisticsReportsSettingsDeterminer(
    config: UsageStatisticsReportsConfig,
    processingTypeStatistics: LoggedUser => Map[ProcessingType, ProcessingTypeUsageStatistics],
    fingerprintFile: File
) {

  def determineSettings(): UsageStatisticsReportsSettings = {
    val queryParams = determineQueryParams()
    val url         = prepareUrl(queryParams)
    UsageStatisticsReportsSettings(config.enabled, url)
  }

  private[statistics] def determineQueryParams(): ListMap[String, String] = {
    // TODO: Warning, here is a security leak. We report statistics in the scope of processing types to which
    //       given user has no access rights.
    val user = NussknackerInternalUser.instance
    val deploymentManagerTypes = processingTypeStatistics(user).values.map(_.deploymentManagerType).map {
      case Some(dm) if knownDeploymentManagerTypes.contains(dm) => dm
      case _                                                    => aggregateForCustomValues
    }
    val dmParams = prepareValuesParams(deploymentManagerTypes, "dm")

    val processingModes = processingTypeStatistics(user).values.map {
      case ProcessingTypeUsageStatistics(_, Some(mode)) if knownProcessingModes.contains(mode) => mode
      case ProcessingTypeUsageStatistics(Some(deploymentManagerType), None)
          if deploymentManagerType.toLowerCase.contains(streamingProcessingMode) =>
        streamingProcessingMode
      case _ => aggregateForCustomValues
    }
    val mParams = prepareValuesParams(processingModes, "m")

    ListMap(
      // We filter out blank fingerprint and source because when smb uses docker-compose, and forwards env variables eg. USAGE_REPORTS_FINGERPRINT
      // from system and the variable doesn't exist, there is no way to skip variable - it can be only set to empty
      "fingerprint" -> config.fingerprint.filterNot(_.isBlank).getOrElse(fingerprint),
      // If it is not set, we assume that it is some custom build from source code
      "source"  -> config.source.filterNot(_.isBlank).getOrElse("sources"),
      "version" -> BuildInfo.version
    ) ++ dmParams ++ mParams
  }

  private def prepareValuesParams(values: Iterable[ProcessingType], metricCategoryKeyPart: String) = {
    val countsParams = values
      .groupBy(identity)
      .mapValuesNow(_.size)
      .map { case (value, count) =>
        s"${metricCategoryKeyPart}_$value" -> count.toString
      }
      .toList
      .sortBy(_._1)
    val singleParamValue = values.toSet.toList match {
      case Nil           => "zero"
      case single :: Nil => single
      case _             => "multiple"
    }
    ListMap(countsParams: _*) + (s"single_$metricCategoryKeyPart" -> singleParamValue)
  }

  private lazy val fingerprint: String = persistedFingerprint {
    randomFingerprint
  }

  private def persistedFingerprint(compute: => String) = {
    Try(FileUtils.readFileToString(fingerprintFile, StandardCharsets.UTF_8)).getOrElse {
      val f = compute
      Try(FileUtils.writeStringToFile(fingerprintFile, f, StandardCharsets.UTF_8))
      f
    }
  }

  private def randomFingerprint = s"gen-${Random.alphanumeric.take(10).mkString}"

}

@JsonCodec final case class UsageStatisticsReportsSettings(enabled: Boolean, url: String)
