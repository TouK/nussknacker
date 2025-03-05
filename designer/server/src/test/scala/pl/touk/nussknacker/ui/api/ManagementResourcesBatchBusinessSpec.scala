package pl.touk.nussknacker.ui.api

import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  RestAssuredVerboseLoggingIfValidationFails
}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithBatchConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{WithBatchDesignerConfig, WithBusinessCaseRestAssuredUsersExtensions}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter.toScenarioGraph

import java.util.UUID

class ManagementResourcesBatchBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithBatchDesignerConfig
    with WithBatchConfigScenarioHelper
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails {

  "The endpoint for testing with parameters should" - {
    "return counts for batch scenario" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(s"""{
                     | "sourceParameters": {
                     |   "sourceId": "sourceId",
                     |   "parameterExpressions": {
                     |     "datetime": {
                     |       "language": "spel",
                     |       "expression": "T(java.time.LocalDateTime).parse('2020-12-31T10:15')"
                     |     },
                     |     "client_id": {
                     |       "language": "spel",
                     |       "expression": "'client1'"
                     |     },
                     |     "amount": {
                     |       "language": "spel",
                     |       "expression": "50"
                     |     },
                     |     "date": {
                     |       "language": "spel",
                     |       "expression": "'2020-12-31'"
                     |     }
                     |   }
                     | },
                     | "scenarioGraph": ${toScenarioGraph(exampleScenario).asJson.spaces2}
                     |}""".stripMargin)
        .post(s"$nuDesignerHttpAddress/api/processManagement/testWithParameters/${exampleScenario.name}")
        .Then()
        .statusCode(200)
        .body(
          "counts.sourceId.all",
          equalTo(1),
          "counts.sinkId.all",
          equalTo(1)
        )
    }
  }

  private lazy val exampleScenarioName = UUID.randomUUID().toString

  private lazy val exampleScenario = ScenarioBuilder
    .streaming(exampleScenarioName)
    .source("sourceId", "table", "Table" -> Expression.spel("'`default_catalog`.`default_database`.`transactions`'"))
    .emptySink(
      "sinkId",
      "table",
      "Table"      -> Expression.spel("'`default_catalog`.`default_database`.`transactions_summary`'"),
      "Raw editor" -> Expression.spel("true"),
      "Value"      -> Expression.spel("#input")
    )

}
