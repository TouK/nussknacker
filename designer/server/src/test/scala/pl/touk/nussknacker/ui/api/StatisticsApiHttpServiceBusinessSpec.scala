package pl.touk.nussknacker.ui.api

import better.files.{File => BetterFile}
import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import io.restassured.response.ValidatableResponse
import org.hamcrest.Matchers.{equalTo, matchesRegex}
import org.hamcrest.{BaseMatcher, Description, Matcher}
import org.mockito.Mockito.when
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.concurrent.Eventually
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.mockito.MockitoSugar
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.base.it.{NuItTest, WithAccessControlCheckingConfigScenarioHelper}
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory.Category1
import pl.touk.nussknacker.test.config.{
  WithAccessControlCheckingConfigRestAssuredUsersExtensions,
  WithAccessControlCheckingDesignerConfig
}
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  RestAssuredVerboseLoggingIfValidationFails
}
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.StatisticName
import pl.touk.nussknacker.ui.statistics._

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneOffset}
import java.util.UUID
import scala.util.{Failure, Random, Success, Try}

class StatisticsApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with NuRestAssureExtensions
    with WithAccessControlCheckingConfigScenarioHelper
    with WithAccessControlCheckingDesignerConfig
    with WithAccessControlCheckingConfigRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with Eventually
    with MockitoSugar
    with Matchers {

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(0.5, Seconds))

  private val nuVersion              = BuildInfo.version
  private val questDbPath            = BetterFile.temp / "nu"
  private val now                    = Instant.now()
  private val yesterday              = now.plus(-1L, ChronoUnit.DAYS)
  private val yesterdayPartitionName = DateTimeFormatter.ISO_LOCAL_DATE.format(yesterday.atZone(ZoneOffset.UTC))
  private val statisticsNames        = StatisticName.values
  private val statisticsNamesSize    = statisticsNames.size
  private val statisticsByIndex      = statisticsNames.zipWithIndex.map(p => p._2 -> p._1).toMap
  private val quote                  = '"'
  private val random                 = new Random()

  private val mockedClock = mock[Clock](new Answer[Instant] {
    override def answer(invocation: InvocationOnMock): Instant = Instant.now()
  })

  override def clock: Clock = mockedClock

  private val exampleScenario = ScenarioBuilder
    .streaming(UUID.randomUUID().toString)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  "The statistic URL endpoint should" - {
    "return single, bare URL without any scenarios details" in {
      given()
        .basicAuthReader()
        .when()
        .get(s"$nuDesignerHttpAddress/api/statistic/usage")
        .Then()
        .statusCode(200)
        .bodyWithStatisticsURL(
          (ComponentsCount.name, new GreaterThanOrEqualToLongMatcher(62L)),
          (NuFingerprint.name, matchesRegex("[\\w-]+?")),
          (NuSource.name, equalTo("sources")),
          (NuVersion.name, equalTo(nuVersion)),
        )
    }

    "return single URL without with scenarios details" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario, category = Category1)
        }
        .basicAuthReader()
        .when()
        .get(s"$nuDesignerHttpAddress/api/statistic/usage")
        .Then()
        .statusCode(200)
        .bodyWithStatisticsURL(
          (AuthorsCount.name, equalTo("1")),
          (AttachmentsTotal.name, equalTo("0")),
          (AttachmentsAverage.name, equalTo("0")),
          (CategoriesCount.name, equalTo("1")),
          (ComponentsCount.name, new GreaterThanOrEqualToLongMatcher(62L)),
          (CommentsTotal.name, equalTo("0")),
          (CommentsAverage.name, equalTo("0")),
          (FragmentsUsedMedian.name, equalTo("0")),
          (FragmentsUsedAverage.name, equalTo("0")),
          (NuFingerprint.name, matchesRegex("[\\w-]+?")),
          (NodesMedian.name, equalTo("2")),
          (NodesMax.name, equalTo("2")),
          (NodesMin.name, equalTo("2")),
          (NodesAverage.name, equalTo("2")),
          (ActiveScenarioCount.name, equalTo("0")),
          (UnknownDMCount.name, equalTo("0")),
          (LiteEmbeddedDMCount.name, equalTo("0")),
          (FlinkDMCount.name, equalTo("1")),
          (LiteK8sDMCount.name, equalTo("0")),
          (FragmentCount.name, equalTo("0")),
          (BoundedStreamCount.name, equalTo("0")),
          (RequestResponseCount.name, equalTo("0")),
          (UnboundedStreamCount.name, equalTo("1")),
          (ScenarioCount.name, equalTo("1")),
          (NuSource.name, equalTo("sources")),
          (UptimeInSecondsMax.name, equalTo("0")),
          (UptimeInSecondsMin.name, equalTo("0")),
          (UptimeInSecondsAverage.name, equalTo("0")),
          (VersionsMedian.name, equalTo("1")),
          (VersionsMax.name, equalTo("1")),
          (VersionsMin.name, equalTo("1")),
          (VersionsAverage.name, equalTo("1")),
          (NuVersion.name, equalTo(nuVersion)),
        )
    }
  }

  "The register statistics endpoint should" - {
    "save statistics asynchronously in DB and return NoContent" in {
      val statistic1 = randomStatisticName()
      val statistic2 = randomStatisticName()
      given()
        .when()
        .basicAuthReader()
        .jsonBody(
          buildRegisterStatisticsRequest(
            statistic1,
            statistic2,
            statistic2
          )
        )
        .post(s"$nuDesignerHttpAddress/api/statistic")
        .Then()
        .statusCode(204)
        .equalsPlainBody("")
        .verifyApplicationState {
          verifyStatisticsExists(
            (statistic1.entryName, new GreaterThanOrEqualToLongMatcher(1)),
            (statistic2.entryName, new GreaterThanOrEqualToLongMatcher(2))
          )
        }
    }

    "recover if DB files from disk are removed" in {
      val statisticName = randomStatisticName()
      given()
        .applicationState {
          removeQuestDBFiles()
        }
        .when()
        .basicAuthReader()
        .jsonBody(buildRegisterStatisticsRequest(statisticName))
        .post(s"$nuDesignerHttpAddress/api/statistic")
        .Then()
        .statusCode(204)
        .equalsPlainBody("")
        .verifyApplicationState {
          verifyStatisticsExists((statisticName.entryName, new GreaterThanOrEqualToLongMatcher(1)))
          questDbPath.exists shouldBe true
        }
    }

    "remove old partitions with periodic job" in {
      val statisticName = randomStatisticName()
      given()
        .applicationState {
          createStatistics(statisticName)
          when(mockedClock.instant()).thenReturn(yesterday, now)
        }
        .when()
        .basicAuthReader()
        .jsonBody(buildRegisterStatisticsRequest(statisticName))
        .post(s"$nuDesignerHttpAddress/api/statistic")
        .Then()
        .statusCode(204)
        .equalsPlainBody("")
        .verifyApplicationState {
          eventually {
            isYesterdayPartitionPresent shouldBe true
          }
          eventually {
            isYesterdayPartitionPresent shouldBe false
          }
        }
    }
  }

  private def verifyStatisticsExists[M <: Comparable[M]](queryParamPairs: (String, Matcher[M])*): Unit = {
    eventually {
      given()
        .basicAuthReader()
        .when()
        .get(s"$nuDesignerHttpAddress/api/statistic/usage")
        .Then()
        .statusCode(200)
        .bodyWithStatisticsURL(queryParamPairs: _*)
    }
  }

  private def randomStatisticName(): StatisticName =
    statisticsByIndex.getOrElse(random.nextInt(statisticsNamesSize), StatisticName.ClickEditDelete)

  private def buildRegisterStatisticsRequest(statisticsNames: StatisticName*): String =
    s"""
      |{
      | "statistics": [
      |  ${statisticsNames.map(name => s"{${quote}name${quote}: ${quote}${name.entryName}${quote}}").mkString(",\n")}
      | ]
      |}""".stripMargin

  private def removeQuestDBFiles(): Unit = {
    questDbPath.delete()
  }

  private def createStatistics(statisticsNames: StatisticName*): Unit =
    given()
      .when()
      .basicAuthReader()
      .jsonBody(buildRegisterStatisticsRequest(statisticsNames: _*))
      .post(s"$nuDesignerHttpAddress/api/statistic")
      .Then()
      .verifyApplicationState {
        verifyStatisticsExists(statisticsNames.map(n => n.entryName -> new GreaterThanOrEqualToLongMatcher(1)): _*)
      }

  private def isYesterdayPartitionPresent = {
    Try {
      questDbPath
        .collectChildren(f => f.name.startsWith(yesterdayPartitionName) && f.isDirectory, maxDepth = 2)
        .hasNext
    }.recover { case _ =>
      false
    }.get
  }

  implicit class BodyWithStatisticsURL[T <: ValidatableResponse](validatableResponse: T) {

    def bodyWithStatisticsURL[M <: Comparable[M]](expectedQueryParams: (String, Matcher[M])*): ValidatableResponse = {
      val url             = validatableResponse.extractString("urls[0]")
      val queryParamsPath = url.replace("https://stats.nussknacker.io/?", "")
      validatableResponse.body(new MatchQueryParams(queryParamsPath, expectedQueryParams))
    }

  }

  private class MatchQueryParams[M <: Comparable[M]](
      queryParamsPath: String,
      queryParamsMatchers: Seq[(String, Matcher[M])]
  ) extends BaseMatcher[ValidatableResponse]
      with LazyLogging {

    override def matches(actual: Any): Boolean = {
      val actualQueryParams = extractQueryParams(queryParamsPath)
      queryParamsMatchers.forall { case (expectedKey, expectedValue) =>
        actualQueryParams.get(expectedKey) match {
          case Some(actualValue) if expectedValue.matches(actualValue) => true
          case Some(actualValue) =>
            logger.info(s"Actual: $actualValue for key: $expectedKey should be expected: $expectedValue")
            false
          case None =>
            logger.info(s"QueryParam with a name: $expectedKey is not present.")
            false
        }
      }
    }

    override def describeTo(description: Description): Unit = description.appendValue(queryParamsMatchers)

    private def extractQueryParams(queryParamsPath: String): Map[String, String] =
      queryParamsPath
        .split("&")
        .map(_.split("=").toList match {
          case (key: String) :: (value: String) :: _ => (key, value)
          case value =>
            throw new IllegalArgumentException(s"Cannot parse query param with value: $value")
        })
        .toList
        .toMap

  }

  private class GreaterThanOrEqualToLongMatcher(expected: Long) extends BaseMatcher[String] {

    override def matches(actual: Any): Boolean = actual match {
      case str: String =>
        Try(str.toLong) match {
          case Failure(_)      => false
          case Success(actual) => actual >= expected
        }
      case _ => false
    }

    override def describeTo(description: Description): Unit = description.appendValue(expected)
  }

}
