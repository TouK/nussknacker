package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  PatientScalaFutures,
  RestAssuredVerboseLogging
}
import pl.touk.nussknacker.tests.base.it.NuItTest
import pl.touk.nussknacker.tests.config.{WithRichDesignerConfig, WithSimplifiedDesignerConfig}

class ScenarioParametersApiSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithRichDesignerConfig
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  "The endpoint for getting scenario parameters combination when" - {
    "authenticated with write access to all categories should" - {
      "return scenario parameters combination for all categories" in {
        given()
          .basicAuth("admin", "admin")
          .when()
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
    }
    "authenticated with write access one category should" - {
      "return scenario parameters combination only for a given category" in {
        given()
          .basicAuth("allpermuser", "allpermuser")
          .when()
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
    }
    "authenticated without write access to category should" - {
      "return empty parameters combination" in {
        given()
          .basicAuth("reader", "reader")
          .when()
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
    "unauthenticated" - {
      "return 401 status code" in {
        given()
          .when()
          .get(s"$nuDesignerHttpAddress/api/scenarioParametersCombinations")
          .Then()
          .statusCode(401)
      }
    }
  }

}
