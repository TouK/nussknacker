package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.{`given`, when}
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig

import java.util.UUID

class StatisticsApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with NuRestAssureExtensions
    with WithSimplifiedConfigScenarioHelper
    with NuRestAssureMatchers {

  private val nuVersion = BuildInfo.version

  private val exampleScenario = ScenarioBuilder
    .streaming(UUID.randomUUID().toString)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  "The statistic URL endpoint when" - {
    "return single, bare URL without any scenarios details" in {
      when()
        .get(s"$nuDesignerHttpAddress/api/statistic/url")
        .Then()
        .statusCode(200)
        .body(
          matchJsonWithRegexValues(
            s"""
               |{
               |  "urls": ["https://stats.nussknacker.io/\\\\?fingerprint=[\\\\w-]+?&source=sources&version=$nuVersion"]
               |}
               |""".stripMargin
          )
        )
    }

    "return single URL without with scenarios details" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .get(s"$nuDesignerHttpAddress/api/statistic/url")
        .Then()
        .statusCode(200)
        .body(
          matchJsonWithRegexValues(
            s"""
               |{
               |  "urls": ["https://stats.nussknacker.io/\\\\?fingerprint=[\\\\w-]+?&s_a=0&s_dm_c=0&s_dm_e=0&s_dm_f=1&
               |s_dm_l=0&s_f=0&s_pm_b=0&s_pm_rr=0&s_pm_s=1&s_s=1&source=sources&version=$nuVersion"]
               |}
               |""".stripMargin.replace("\n", "")
          )
        )
    }
  }

}
