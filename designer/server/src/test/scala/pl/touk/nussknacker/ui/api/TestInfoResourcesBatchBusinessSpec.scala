package pl.touk.nussknacker.ui.api

import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.test.base.it.{NuItTest, WithBatchConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{WithBatchDesignerConfig, WithBusinessCaseRestAssuredUsersExtensions}
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  RestAssuredVerboseLoggingIfValidationFails
}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter.toScenarioGraph

import java.util.UUID

class TestInfoResourcesBatchBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithBatchDesignerConfig
    with WithBatchConfigScenarioHelper
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails {

  "The endpoint for test data generation should" - {
    "return 200 and random results" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(toScenarioGraph(exampleScenario).asJson.spaces2)
        .post(s"$nuDesignerHttpAddress/api/testInfo/${exampleScenario.name}/generate/10")
        .Then()
        .statusCode(200)
//      TODO: add assertion for random results
    }
  }

  private lazy val exampleScenarioName = UUID.randomUUID().toString

  private lazy val exampleScenario = ScenarioBuilder
    .streaming(exampleScenarioName)
    .source("sourceId", "table", "Table" -> Expression.spel("'transactions'"))
    .emptySink("end", "dead-end")

}
