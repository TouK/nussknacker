package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.{
  WithAccessControlCheckingConfigRestAssuredUsersExtensions,
  WithAccessControlCheckingDesignerConfig
}
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLoggingIfValidationFails}

class ScenarioParametersApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithAccessControlCheckingDesignerConfig
    with WithAccessControlCheckingConfigRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures {

  "The endpoint for getting scenario parameters combination when" - {
    "authenticated should" - {
      "return scenario parameters combination for all categories for a user with access to all categories" in {
        given()
          .when()
          .basicAuthAdmin()
          .get(s"$nuDesignerHttpAddress/api/scenarioParametersCombinations")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
               |  "combinations": [
               |    {
               |      "processingMode": "Unbounded-Stream",
               |      "category": "Category1",
               |      "engineSetupName": "Flink"
               |    },
               |    {
               |      "processingMode": "Unbounded-Stream",
               |      "category": "Category2",
               |      "engineSetupName": "Flink"
               |    }
               |  ],
               |  "engineSetupErrors": {}
               |}""".stripMargin
          )
      }
      "return parameters combination for categories the user has a write access" in {
        given()
          .when()
          .basicAuthLimitedWriter()
          .get(s"$nuDesignerHttpAddress/api/scenarioParametersCombinations")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
               |  "combinations": [
               |    {
               |      "processingMode": "Unbounded-Stream",
               |      "category": "Category1",
               |      "engineSetupName": "Flink"
               |    }
               |  ],
               |  "engineSetupErrors": {}
               |}""".stripMargin
          )
      }
      "return no parameters combination for categories the user has NOT a write access" in {
        given()
          .when()
          .basicAuthLimitedReader()
          .get(s"$nuDesignerHttpAddress/api/scenarioParametersCombinations")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            """{
              |  "combinations": [],
              |  "engineSetupErrors": {}
              |}""".stripMargin
          )
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .when()
          .basicAuthUnknownUser()
          .get(s"$nuDesignerHttpAddress/api/scenarioParametersCombinations")
          .Then()
          .statusCode(401)
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and no parameters combination because of no write access" in {
        given()
          .when()
          .noAuth()
          .get(s"$nuDesignerHttpAddress/api/scenarioParametersCombinations")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            """{
              |  "combinations": [],
              |  "engineSetupErrors": {}
              |}""".stripMargin
          )
      }
    }
  }

}
