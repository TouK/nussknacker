package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.{WithBusinessCaseRestAssuredUsersExtensions, WithSimplifiedDesignerConfig}

class ScenarioParametersApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures {

  "The endpoint for getting scenario parameters combination should" - {
    "return scenario parameters combination for all categories" in {
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
             |      "engineSetupName": "Mockable"
             |    }
             |  ],
             |  "engineSetupErrors": {}
             |}""".stripMargin
        )
    }
  }

}
