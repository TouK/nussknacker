package pl.touk.nussknacker

import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, VeryPatientScalaFutures}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter.toScenarioGraph

class BatchDataGenerationSpec
    extends AnyFreeSpecLike
    with DockerBasedInstallationExampleNuEnvironment
    with Matchers
    with VeryPatientScalaFutures
    with NuRestAssureExtensions
    with NuRestAssureMatchers {

  "Batch scenario generate file function should generate random results according to defined schema" in {
    given()
      .when()
      .request()
      .preemptiveBasicAuth("admin", "admin")
      .jsonBody(s"""
          |{
          |    "name" : "${simpleBatchTableScenario.name}",
          |    "category" : "Default",
          |    "isFragment" : false,
          |    "processingMode" : "Bounded-Stream"
          |}
          |""".stripMargin)
      .post("http://localhost:8080/api/processes")
      .Then()
      .statusCode(201)

    val testResultContent = given()
      .when()
      .request()
      .preemptiveBasicAuth("admin", "admin")
      .jsonBody(toScenarioGraph(simpleBatchTableScenario).asJson.spaces2)
      .post(s"http://localhost:8080/api/testInfo/${simpleBatchTableScenario.name}/generate/10")
      .Then()
      .statusCode(200)
      .extract()
      .body()
      .asString()

    val expectedRegex =
      """|\{
         |   "sourceId":"sourceId",
         |   "record":
         |     \{
         |         "datetime":"[0-9T.:-]*",
         |         "client_id":"[a-z0-9]*",
         |         "amount":[a-z0-9.]*,
         |         "date":"[a-z0-9]*"
         |     \}
         |\}
         |""".stripMargin.replace("\n", "").replace(" ", "")

    testResultContent.split('\n').head should fullyMatch regex expectedRegex
  }

  private lazy val simpleBatchTableScenario = ScenarioBuilder
    .streaming("SumTransactions")
    .source("sourceId", "table", "Table" -> Expression.spel("'transactions'"))
    .emptySink("end", "dead-end")

}
