package pl.touk.nussknacker.ui.statistics

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig

import scala.collection.immutable.ListMap

class UsageStatisticsHtmlSnippetTest extends AnyFunSuite with Matchers {

  val sampleFingerprint = "fooFingerprint"

  test("should generate correct url with encoded paramsForSingleMode") {
    UsageStatisticsHtmlSnippet.prepareUrl(ListMap("f" -> "a b", "v" -> "1.6.5-a&b=c")) shouldBe "https://stats.nussknacker.io/?f=a+b&v=1.6.5-a%26b%3Dc"
  }

  test("should generated statically defined query paramsForSingleMode") {
    val params = UsageStatisticsHtmlSnippet.prepareQueryParams(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint)),
      Map.empty)
    params should contain ("fingerprint" -> sampleFingerprint)
    params should contain ("version" -> BuildInfo.version)
  }

  test("should generated random fingerprint if configured is blank") {
    val params = UsageStatisticsHtmlSnippet.prepareQueryParams(
      UsageStatisticsReportsConfig(enabled = true, Some("")),
      Map.empty)
    params("fingerprint") should startWith ("gen-")
  }

  test("should generated query params for each deployment manager and with single deployment manager field") {
    val givenFooDm = "fooDm"
    val paramsForSingleDm = UsageStatisticsHtmlSnippet.prepareQueryParams(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint)),
      Map("streaming" -> ProcessingTypeUsageStatistics(givenFooDm, None)))
    paramsForSingleDm should contain ("single_dm" -> givenFooDm)
    paramsForSingleDm should contain ("dm_" + givenFooDm -> "1")

    val givenBarDm = "barDm"
    val paramsForMultipleDms = UsageStatisticsHtmlSnippet.prepareQueryParams(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint)),
      Map(
        "streaming" -> ProcessingTypeUsageStatistics(givenFooDm, None),
        "streaming2" -> ProcessingTypeUsageStatistics(givenBarDm, None),
        "streaming3" -> ProcessingTypeUsageStatistics(givenFooDm, None)))
    paramsForMultipleDms.keys should not contain "single_dm"
    paramsForMultipleDms should contain ("dm_" + givenFooDm -> "2")
    paramsForMultipleDms should contain ("dm_" + givenBarDm -> "1")
  }

  test("should generated query params for each processing mode and with single processing mode field") {
    val streamingMode = "streaming"
    val paramsForSingleMode = UsageStatisticsHtmlSnippet.prepareQueryParams(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint)),
      Map("streaming" -> ProcessingTypeUsageStatistics("fooDm", Some(streamingMode))))
    paramsForSingleMode should contain ("single_m" -> streamingMode)
    paramsForSingleMode should contain ("m_" + streamingMode -> "1")

    val requestResponseMode = "request-response"
    val paramsForMultipleModes = UsageStatisticsHtmlSnippet.prepareQueryParams(
      UsageStatisticsReportsConfig(enabled = true, Some(sampleFingerprint)),
      Map(
        "streaming" -> ProcessingTypeUsageStatistics("fooDm", Some(streamingMode)),
        "streaming2" -> ProcessingTypeUsageStatistics("barDm", Some(requestResponseMode)),
        "streaming3" -> ProcessingTypeUsageStatistics("bazDm", Some(streamingMode))))
    paramsForMultipleModes.keys should not contain "single_m"
    paramsForMultipleModes should contain ("m_" + streamingMode -> "2")
    paramsForMultipleModes should contain ("m_" + requestResponseMode -> "1")
  }

}
