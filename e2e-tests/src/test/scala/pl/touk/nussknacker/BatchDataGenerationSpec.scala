package pl.touk.nussknacker

import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.text.MatchesPattern
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, VeryPatientScalaFutures}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter.toScenarioGraph
import pl.touk.nussknacker.engine.spel.SpelExtension._

class BatchDataGenerationSpec
    extends AnyFreeSpecLike
    with DockerBasedInstallationExampleNuEnvironment
    with Matchers
    with VeryPatientScalaFutures
    with NuRestAssureExtensions
    with NuRestAssureMatchers {

  private val simpleBatchTableScenario = ScenarioBuilder
    .streaming("SumTransactions")
    .source("sourceId", "table", "Table" -> "'transactions'".spel)
    .emptySink("end", "dead-end")

  "Generate file endpoint should generate records with randomized values" in {
//    TODO local: how to do this cleanly?
    val flinkDateTimeRegex                = """\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3}"""
    val flinkDatagenStringRegex           = """[a-z\d]{100}"""
    val flinkDecimalWith15Precision2Scale = """\d{13}\.\d{0,2}"""
    val recordRegex =
      s"""\\{"datetime":"$flinkDateTimeRegex","client_id":"$flinkDatagenStringRegex","amount":$flinkDecimalWith15Precision2Scale\\}"""
    val expectedRegex = s"""\\{"sourceId":"sourceId","record":$recordRegex\\}"""

    given()
      .applicationState(
        createBatchScenario(simpleBatchTableScenario.name.value)
      )
      .when()
      .request()
      .preemptiveBasicAuth("admin", "admin")
      .jsonBody(toScenarioGraph(simpleBatchTableScenario).asJson.spaces2)
      .post(s"http://localhost:8080/api/testInfo/SumTransactions/generate/1")
      .Then()
      .statusCode(200)
      .body(MatchesPattern.matchesPattern(expectedRegex))
  }

  private def createBatchScenario(scenarioName: String): Unit = {
    given()
      .when()
      .request()
      .preemptiveBasicAuth("admin", "admin")
      .jsonBody(s"""
                   |{
                   |    "name" : "$scenarioName",
                   |    "category" : "Default",
                   |    "isFragment" : false,
                   |    "processingMode" : "Bounded-Stream"
                   |}
                   |""".stripMargin)
      .post("http://localhost:8080/api/processes")
      .Then()
      .statusCode(201)
  }

}
