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

//  TODO: This case works when testing from IDEA manually, but in test IllegalAccessError is thrown:
//   class org.apache.flink.table.catalog.ContextResolvedTable tried to access method 'org.apache.flink.table.catalog.ObjectIdentifier org.apache.flink.table.catalog.ObjectIdentifier.ofAnonymous(java.lang.String)'
//   maybe because of classloader juggling? but why doesn't it blow up when launching from IDEA manually?
  "The endpoint for dumping source data from table should" - {
    "return counts for batch scenario" in {
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
//      TODO: add assertion for source data results in response body
    }
  }

  private lazy val exampleScenarioName = UUID.randomUUID().toString

  private lazy val exampleScenario = ScenarioBuilder
    .streaming(exampleScenarioName)
    .source("sourceId", "table", "Table" -> Expression.spel("'transactions'"))
    .emptySink("end", "dead-end")

}
