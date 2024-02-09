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
import pl.touk.nussknacker.ui.api.helpers.{NuItTest, NuTestScenarioManager}

class ScenarioParametersApiSpec
    extends AnyFreeSpecLike
    with NuItTest
    with NuTestScenarioManager
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
          .equalsJsonBody(s"""[
                             |    {
                             |        "processingMode": "Unbounded-Stream",
                             |        "category": "Category1",
                             |        "engineSetup": {
                             |            "name": "Flink",
                             |            "errors": [
                             |
                             |            ]
                             |        }
                             |    },
                             |    {
                             |        "processingMode": "Unbounded-Stream",
                             |        "category": "Category2",
                             |        "engineSetup": {
                             |            "name": "Flink",
                             |            "errors": [
                             |
                             |            ]
                             |        }
                             |    }
                             |]""".stripMargin)
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
          .equalsJsonBody(s"""
               |[
               |    {
               |        "processingMode": "Unbounded-Stream",
               |        "category": "Category1",
               |        "engineSetup": {
               |            "name": "Flink",
               |            "errors": [
               |
               |            ]
               |        }
               |    }
               |]""".stripMargin)
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
          .equalsJsonBody("[]")
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
