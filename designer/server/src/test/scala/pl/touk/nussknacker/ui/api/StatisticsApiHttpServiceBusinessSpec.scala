package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.base.it.{NuItTest, WithAccessControlCheckingConfigScenarioHelper}
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory.Category1
import pl.touk.nussknacker.test.config.{
  WithAccessControlCheckingConfigRestAssuredUsersExtensions,
  WithAccessControlCheckingDesignerConfig
}
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers}

import java.util.UUID

class StatisticsApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with NuRestAssureExtensions
    with WithAccessControlCheckingConfigScenarioHelper
    with WithAccessControlCheckingDesignerConfig
    with WithAccessControlCheckingConfigRestAssuredUsersExtensions
    with NuRestAssureMatchers {

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
        .body(
          matchJsonWithRegexValues(
            s"""
               |{
               |  "urls": ["https://stats.nussknacker.io/\\\\?c_n=[0-9]+&fingerprint=[\\\\w-]+?&source=sources&version=$nuVersion"]
               |}
               |""".stripMargin
          )
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
        .body(
          matchJsonWithRegexValues(
            s"""
               |{
               |  "urls": ["https://stats.nussknacker.io/\\\\?a_n=1&c=1&c_n=[0-9]+&e_a=0&fingerprint=[\\\\w-]+?&m_a=0&
               |m_c=0&m_f=0&m_n=2&m_u=0&s_a=0&s_dm_c=0&s_dm_e=0&s_dm_f=1&s_dm_l=0&s_f=0&s_pm_b=0&s_pm_rr=0&s_pm_s=1&
               |s_s=1&source=sources&v_a=0&v_c=0&v_f=0&v_m=1&v_n=2&v_u=0&v_v=1&version=$nuVersion"]
               |}
               |""".stripMargin.replace("\n", "")
          )
        )
    }
  }

}
