package pl.touk.nussknacker.ui.statistics

import org.apache.commons.io.FileUtils
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig

import java.io.File
import java.nio.charset.StandardCharsets
import scala.collection.immutable.ListMap

class UsageStatisticsReportsSettingsDeterminerTest extends AnyFunSuite with Matchers with OptionValues {

  val sampleFingerprint = "fooFingerprint"

  test("should generate correct url with encoded paramsForSingleMode") {
    UsageStatisticsReportsSettingsDeterminer.prepareUrl(
      ListMap("f" -> "a b", "v" -> "1.6.5-a&b=c")
    ) shouldBe "https://stats.nussknacker.io/?f=a+b&v=1.6.5-a%26b%3Dc"
  }

  test("should generated statically defined query paramsForSingleMode") {
    val params = UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      _ => Map.empty[ProcessingType, ProcessingTypeUsageStatistics]
    ).determineQueryParams()
    params should contain("fingerprint" -> sampleFingerprint)
    params should contain("source" -> "sources")
    params should contain("version" -> BuildInfo.version)
  }

  test("should generated random fingerprint if configured is blank") {
    val params = UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, Some(""), None),
      _ => Map.empty[ProcessingType, ProcessingTypeUsageStatistics]
    ).determineQueryParams()
    params("fingerprint") should startWith("gen-")
  }

  test("should read persisted fingerprint") {
    val fingerprintFile = File.createTempFile("nussknacker", ".fingerprint")
    fingerprintFile.deleteOnExit()
    val savedFingerprint = "foobarbaz123"
    FileUtils.writeStringToFile(fingerprintFile, savedFingerprint, StandardCharsets.UTF_8)
    val params = new UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, None, None),
      _ => Map.empty,
      fingerprintFile
    ).determineQueryParams()
    params.get("fingerprint").value shouldEqual savedFingerprint
  }

  test("should save persisted fingerprint") {
    val fingerprintFile = File.createTempFile("nussknacker", ".fingerprint")
    fingerprintFile.deleteOnExit()
    val params = new UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, None, None),
      _ => Map.empty,
      fingerprintFile
    ).determineQueryParams()
    val generatedFingerprint = params.get("fingerprint").value
    val fingerprintInFile    = FileUtils.readFileToString(fingerprintFile, StandardCharsets.UTF_8)
    fingerprintInFile shouldEqual generatedFingerprint
  }

  test("should generated query params for each deployment manager and with single deployment manager field") {
    val givenDm1 = "flinkStreaming"
    val paramsForSingleDm = UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      _ => Map("streaming" -> ProcessingTypeUsageStatistics(Some(givenDm1), None))
    ).determineQueryParams()
    paramsForSingleDm should contain("single_dm" -> givenDm1)
    paramsForSingleDm should contain("dm_" + givenDm1 -> "1")

    val givenDm2 = "lite-k8s"
    val paramsForMultipleDms = UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      _ =>
        Map(
          "streaming"  -> ProcessingTypeUsageStatistics(Some(givenDm1), None),
          "streaming2" -> ProcessingTypeUsageStatistics(Some(givenDm2), None),
          "streaming3" -> ProcessingTypeUsageStatistics(Some(givenDm1), None)
        )
    ).determineQueryParams()
    paramsForMultipleDms should contain("single_dm" -> "multiple")
    paramsForMultipleDms should contain("dm_" + givenDm1 -> "2")
    paramsForMultipleDms should contain("dm_" + givenDm2 -> "1")
  }

  test("should generated query params for each processing mode and with single processing mode field") {
    val streamingMode = "streaming"
    val paramsForSingleMode = UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      _ => Map("streaming" -> ProcessingTypeUsageStatistics(Some("fooDm"), Some(streamingMode)))
    ).determineQueryParams()
    paramsForSingleMode should contain("single_m" -> streamingMode)
    paramsForSingleMode should contain("m_" + streamingMode -> "1")

    val requestResponseMode = "request-response"
    val paramsForMultipleModes = UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      _ =>
        Map(
          "streaming"  -> ProcessingTypeUsageStatistics(Some("fooDm"), Some(streamingMode)),
          "streaming2" -> ProcessingTypeUsageStatistics(Some("barDm"), Some(requestResponseMode)),
          "streaming3" -> ProcessingTypeUsageStatistics(Some("bazDm"), Some(streamingMode))
        )
    ).determineQueryParams()
    paramsForMultipleModes should contain("single_m" -> "multiple")
    paramsForMultipleModes should contain("m_" + streamingMode -> "2")
    paramsForMultipleModes should contain("m_" + requestResponseMode -> "1")
  }

  test("should aggregate unknown deployment manager and processing mode as a custom") {
    val givenCustomDm = "customDm"
    val paramsForSingleDm = UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      _ => Map("streaming" -> ProcessingTypeUsageStatistics(Some(givenCustomDm), None))
    ).determineQueryParams()
    paramsForSingleDm should contain("single_dm" -> "custom")
    paramsForSingleDm should contain("dm_custom" -> "1")

    val customMode = "customMode"
    val paramsForSingleMode = UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      _ => Map("streaming" -> ProcessingTypeUsageStatistics(Some("fooDm"), Some(customMode)))
    ).determineQueryParams()
    paramsForSingleMode should contain("single_m" -> "custom")
    paramsForSingleMode should contain("m_custom" -> "1")
  }

  test("should handle missing manager type") {
    val paramsForSingleDmWithoutType = UsageStatisticsReportsSettingsDeterminer(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      _ => Map("streaming" -> ProcessingTypeUsageStatistics(None, None))
    ).determineQueryParams()
    paramsForSingleDmWithoutType should contain("single_dm" -> "custom")
    paramsForSingleDmWithoutType should contain("dm_custom" -> "1")
  }

}
