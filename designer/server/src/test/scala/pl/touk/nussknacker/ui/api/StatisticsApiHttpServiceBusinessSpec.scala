package pl.touk.nussknacker.ui.api

import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import io.restassured.response.ValidatableResponse
import org.hamcrest.Matchers.{equalTo, matchesRegex}
import org.hamcrest.{BaseMatcher, Description, Matcher}
import org.scalatest.concurrent.Eventually
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.base.it.{NuItTest, WithAccessControlCheckingConfigScenarioHelper}
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory.Category1
import pl.touk.nussknacker.test.config.{
  WithAccessControlCheckingConfigRestAssuredUsersExtensions,
  WithAccessControlCheckingDesignerConfig
}
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, RestAssuredVerboseLogging}

import java.util.UUID
import scala.util.{Failure, Success, Try}

class StatisticsApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with NuRestAssureExtensions
    with WithAccessControlCheckingConfigScenarioHelper
    with WithAccessControlCheckingDesignerConfig
    with WithAccessControlCheckingConfigRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with Eventually {

  private val nuVersion = BuildInfo.version

  private val exampleScenario = ScenarioBuilder
    .streaming(UUID.randomUUID().toString)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  "The statistic URL endpoint when" - {
    "return single, bare URL without any scenarios details" in {
      given()
        .basicAuthReader()
        .when()
        .get(s"$nuDesignerHttpAddress/api/statistic/usage")
        .Then()
        .statusCode(200)
        .bodyWithStatisticsURL(
          ("c_n", new GreaterThanOrEqualToLongMatcher(62L)),
          ("fingerprint", matchesRegex("[\\w-]+?")),
          ("source", equalTo("sources")),
          ("version", equalTo(nuVersion)),
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
          ("a_n", equalTo("1")),
          ("a_t", equalTo("0")),
          ("a_v", equalTo("0")),
          ("c", equalTo("1")),
          ("c_n", new GreaterThanOrEqualToLongMatcher(62L)),
          ("c_t", equalTo("0")),
          ("c_v", equalTo("0")),
          ("f_m", equalTo("0")),
          ("f_v", equalTo("0")),
          ("fingerprint", matchesRegex("[\\w-]+?")),
          ("n_m", equalTo("2")),
          ("n_ma", equalTo("2")),
          ("n_mi", equalTo("2")),
          ("n_v", equalTo("2")),
          ("s_a", equalTo("0")),
          ("s_dm_c", equalTo("0")),
          ("s_dm_e", equalTo("0")),
          ("s_dm_f", equalTo("1")),
          ("s_dm_l", equalTo("0")),
          ("s_f", equalTo("0")),
          ("s_pm_b", equalTo("0")),
          ("s_pm_rr", equalTo("0")),
          ("s_pm_s", equalTo("1")),
          ("s_s", equalTo("1")),
          ("source", equalTo("sources")),
          ("u_ma", equalTo("0")),
          ("u_mi", equalTo("0")),
          ("u_v", equalTo("0")),
          ("v_m", equalTo("1")),
          ("v_ma", equalTo("1")),
          ("v_mi", equalTo("1")),
          ("v_v", equalTo("1")),
          ("version", equalTo(nuVersion)),
        )
    }
  }

  "The register statistics endpoint when" - {
    "authenticated should" - {
      "save statistics asynchronously in DB and return NoContent" in {
        given()
          .when()
          .basicAuthReader()
          .jsonBody("""
              |{
              | "statistics": [
              |  {"name": "SEARCH_SCENARIOS_BY_NAME"},
              |  {"name": "FILTER_SCENARIOS_BY_STATUS"},
              |  {"name": "SEARCH_SCENARIOS_BY_NAME"}
              | ]
              |}""".stripMargin)
          .post(s"$nuDesignerHttpAddress/api/statistic")
          .Then()
          .statusCode(204)
          .equalsPlainBody("")
          .verifyApplicationState {
            verifyStatisticsExists(
              ("FILTER_SCENARIOS_BY_STATUS", new GreaterThanOrEqualToLongMatcher(1)),
              ("SEARCH_SCENARIOS_BY_NAME", new GreaterThanOrEqualToLongMatcher(2))
            )
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

    override def matches(actual: Any): Boolean = extractQueryParams(queryParamsPath) match {
      case Some(actualQueryParams) =>
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
      case None =>
        logger.info("Cannot extract query params")
        false
    }

    override def describeTo(description: Description): Unit = description.appendValue(queryParamsMatchers)

    private def extractQueryParams(queryParamsPath: String): Option[Map[String, String]] =
      queryParamsPath
        .split("&")
        .map(_.split("=").toList match {
          case (key: String) :: (value: String) :: _ => Some((key, value))
          case _                                     => None
        })
        .toList
        .sequence
        .map(_.toMap)

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
