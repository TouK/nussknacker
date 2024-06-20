package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.test.base.it.{NuItTest, WithAccessControlCheckingConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithAccessControlCheckingConfigRestAssuredUsersExtensions,
  WithAccessControlCheckingDesignerConfig
}
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers}
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.StatisticName.SearchScenariosByName

class StatisticsApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithAccessControlCheckingConfigScenarioHelper
    with WithAccessControlCheckingDesignerConfig
    with WithAccessControlCheckingConfigRestAssuredUsersExtensions
    with NuRestAssureExtensions
    with NuRestAssureMatchers {

  "The statistic URL endpoint when" - {
    "authenticated should" - {
      "return single URL" in {
        given()
          .when()
          .basicAuthReader()
          .get(s"$nuDesignerHttpAddress/api/statistic/usage")
          .Then()
          .statusCode(200)
          .body(
            matchJsonWithRegexValues(
              s"""
                 |{
                 |  "urls": ["https://stats.nussknacker.io/\\\\?(?:.+=.+\\\\&?)+"]
                 |}
                 |""".stripMargin
            )
          )
      }
    }

    // todo what about anonymous user
    "not authenticated should" - {
      "forbid access" in {
        given()
          .when()
          .basicAuthUnknownUser()
          .get(s"$nuDesignerHttpAddress/api/statistic/usage")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
  }

  "The register statistics endpoint when" - {
    "authenticated should" - {
      "return NoContent" in {
        given()
          .when()
          .basicAuthReader()
          .jsonBody(s"""{"statistics": [{"name": "${SearchScenariosByName.entryName}"}]}""")
          .post(s"$nuDesignerHttpAddress/api/statistic")
          .Then()
          .statusCode(204)
          .equalsPlainBody("")
      }
    }

    // todo what about anonymous user
    "not authenticated should" - {
      "forbid access" in {
        given()
          .when()
          .basicAuthUnknownUser()
          .jsonBody(s"""{"statistics": [{"name": "${SearchScenariosByName.entryName}"}]}""")
          .post(s"$nuDesignerHttpAddress/api/statistic")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
  }

}
