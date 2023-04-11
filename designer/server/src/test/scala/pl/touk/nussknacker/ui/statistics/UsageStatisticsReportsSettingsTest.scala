package pl.touk.nussknacker.ui.statistics

import org.apache.commons.io.FileUtils
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig

import java.io.File
import java.nio.charset.StandardCharsets
import scala.collection.immutable.ListMap

class UsageStatisticsReportsSettingsTest extends AnyFunSuite with Matchers with OptionValues with BeforeAndAfterEach {

  val sampleFingerprint = "fooFingerprint"

  test("should generate correct url with encoded paramsForSingleMode") {
    UsageStatisticsReportsSettings.prepareUrl(ListMap("f" -> "a b", "v" -> "1.6.5-a&b=c")) shouldBe "https://stats.nussknacker.io/?f=a+b&v=1.6.5-a%26b%3Dc"
  }

  test("should generated statically defined query paramsForSingleMode") {
    val params = UsageStatisticsReportsSettings.prepareQueryParams(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      Map.empty)
    params should contain ("fingerprint" -> sampleFingerprint)
    params should contain ("source" -> "sources")
    params should contain ("version" -> BuildInfo.version)
  }

  test("should generated random fingerprint if configured is blank") {
    val params = UsageStatisticsReportsSettings.prepareQueryParams(
      UsageStatisticsReportsConfig(enabled = true, Some(""), None),
      Map.empty)
    params("fingerprint") should startWith ("gen-")
  }

  test("should read persisted fingerprint") {
    val fingerprintFile = File.createTempFile("nussknacker", ".fingerprint")
    fingerprintFile.deleteOnExit()
    val savedFingerprint = "foobarbaz123"
    FileUtils.writeStringToFile(fingerprintFile, savedFingerprint, StandardCharsets.UTF_8)
    val params = UsageStatisticsReportsSettings.prepareQueryParams(
      UsageStatisticsReportsConfig(enabled = true, None, None),
      Map.empty,
      fingerprintFile)
    params.get("fingerprint").value shouldEqual savedFingerprint
  }

  test("should save persisted fingerprint") {
    val fingerprintFile = File.createTempFile("nussknacker", ".fingerprint")
    fingerprintFile.deleteOnExit()
    val params = UsageStatisticsReportsSettings.prepareQueryParams(
      UsageStatisticsReportsConfig(enabled = true, None, None),
      Map.empty,
      fingerprintFile)
    val generatedFingerprint = params.get("fingerprint").value
    val fingerprintInFile = FileUtils.readFileToString(fingerprintFile, StandardCharsets.UTF_8)
    fingerprintInFile shouldEqual generatedFingerprint
  }

  test("should generated query params for each deployment manager and with single deployment manager field") {
    val givenDm1 = "flinkStreaming"
    val paramsForSingleDm = UsageStatisticsReportsSettings.prepareQueryParams(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      Map("streaming" -> ProcessingTypeUsageStatistics(givenDm1, None)))
    paramsForSingleDm should contain ("single_dm" -> givenDm1)
    paramsForSingleDm should contain ("dm_" + givenDm1 -> "1")

    val givenDm2 = "lite-k8s"
    val paramsForMultipleDms = UsageStatisticsReportsSettings.prepareQueryParams(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      Map(
        "streaming" -> ProcessingTypeUsageStatistics(givenDm1, None),
        "streaming2" -> ProcessingTypeUsageStatistics(givenDm2, None),
        "streaming3" -> ProcessingTypeUsageStatistics(givenDm1, None)))
    paramsForMultipleDms should contain ("single_dm" -> "multiple")
    paramsForMultipleDms should contain ("dm_" + givenDm1 -> "2")
    paramsForMultipleDms should contain ("dm_" + givenDm2 -> "1")
  }

  test("should generated query params for each processing mode and with single processing mode field") {
    val streamingMode = "streaming"
    val paramsForSingleMode = UsageStatisticsReportsSettings.prepareQueryParams(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      Map("streaming" -> ProcessingTypeUsageStatistics("fooDm", Some(streamingMode))))
    paramsForSingleMode should contain ("single_m" -> streamingMode)
    paramsForSingleMode should contain ("m_" + streamingMode -> "1")

    val requestResponseMode = "request-response"
    val paramsForMultipleModes = UsageStatisticsReportsSettings.prepareQueryParams(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      Map(
        "streaming" -> ProcessingTypeUsageStatistics("fooDm", Some(streamingMode)),
        "streaming2" -> ProcessingTypeUsageStatistics("barDm", Some(requestResponseMode)),
        "streaming3" -> ProcessingTypeUsageStatistics("bazDm", Some(streamingMode))))
    paramsForMultipleModes should contain ("single_m" -> "multiple")
    paramsForMultipleModes should contain ("m_" + streamingMode -> "2")
    paramsForMultipleModes should contain ("m_" + requestResponseMode -> "1")
  }

  test("should aggregate unknown deployment manager and processing mode as a custom") {
    val givenCustomDm = "customDm"
    val paramsForSingleDm = UsageStatisticsReportsSettings.prepareQueryParams(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      Map("streaming" -> ProcessingTypeUsageStatistics(givenCustomDm, None)))
    paramsForSingleDm should contain("single_dm" -> "custom")
    paramsForSingleDm should contain("dm_custom" -> "1")

    val customMode = "customMode"
    val paramsForSingleMode = UsageStatisticsReportsSettings.prepareQueryParams(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint), None),
      Map("streaming" -> ProcessingTypeUsageStatistics("fooDm", Some(customMode))))
    paramsForSingleMode should contain ("single_m" -> "custom")
    paramsForSingleMode should contain ("m_custom" -> "1")
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    UsageStatisticsReportsSettings.invalidateCache()
  }

}
