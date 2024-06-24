package pl.touk.nussknacker.batch

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
    with DockerBasedBatchExampleNuEnvironment
    with Matchers
    with VeryPatientScalaFutures
    with NuRestAssureExtensions
    with NuRestAssureMatchers {

  "Batch scenario generate file function should generate random results according to defined schema" in {
    given()
      .when()
      .request()
      .preemptiveBasicAuth("admin", "admin")
      .jsonBody(toScenarioGraph(exampleScenario).asJson.spaces2)
      .post(s"$nginxServiceUrl/api/testInfo/SumTransactions/generate/10")
      .Then()
      .statusCode(200)
    //  TODO: add assertion for random results
  }

  private lazy val exampleScenario = ScenarioBuilder
    .streaming("SumTransactions")
    .source("sourceId", "table", "Table" -> Expression.spel("'transactions'"))
    .emptySink("end", "dead-end")

}
