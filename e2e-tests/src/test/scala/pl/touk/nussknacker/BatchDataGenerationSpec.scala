package pl.touk.nussknacker

import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.config.WithE2EInstallationExampleRestAssuredUsersExtensions
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, VeryPatientScalaFutures}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter.toScenarioGraph

class BatchDataGenerationSpec
    extends AnyFreeSpecLike
    with DockerBasedInstallationExampleNuEnvironment
    with Matchers
    with VeryPatientScalaFutures
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with WithE2EInstallationExampleRestAssuredUsersExtensions {

  private val simpleBatchTableScenario = ScenarioBuilder
    .streaming("SumTransactions")
    .source("sourceId", "table", "Table" -> "'transactions'".spel)
    .emptySink("end", "dead-end")

  private val designerServiceUrl = "http://localhost:8080"

  "Generate file endpoint should generate records with randomized values for scenario with table source" in {
    val expectedRecordRegex =
      s"""{
         |   "sourceId": "sourceId",
         |   "record": {
         |      "datetime": "${regexes.localDateRegex}",
         |      "client_id": "[a-z\\\\d]{100}",
         |      "amount": "${regexes.decimalRegex}"
         |   }
         |}""".stripMargin

    given()
      .applicationState(
        createBatchScenario(simpleBatchTableScenario.name.value)
      )
      .when()
      .request()
      .basicAuthAdmin()
      .jsonBody(toScenarioGraph(simpleBatchTableScenario).asJson.spaces2)
      .post(
        s"$designerServiceUrl/api/testInfo/${simpleBatchTableScenario.name.value}/generate/10"
      )
      .Then()
      .statusCode(200)
      .body(
        matchAllNdJsonWithRegexValues(expectedRecordRegex)
      )
  }

  private def createBatchScenario(scenarioName: String): Unit = {
    given()
      .when()
      .request()
      .basicAuthAdmin()
      .jsonBody(s"""
                   |{
                   |    "name" : "$scenarioName",
                   |    "category" : "Default",
                   |    "isFragment" : false,
                   |    "processingMode" : "Bounded-Stream"
                   |}
                   |""".stripMargin)
      .post(s"$designerServiceUrl/api/processes")
      .Then()
      .statusCode(201)
  }

}
