package pl.touk.nussknacker

import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.config.WithE2EInstallationExampleRestAssuredUsersExtensions
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.test.{NuRestAssureExtensions, VeryPatientScalaFutures}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter.toScenarioGraph

class BatchDataGenerationSpec
    extends AnyFreeSpecLike
    with DockerBasedInstallationExampleNuEnvironment
    with Matchers
    with VeryPatientScalaFutures
    with NuRestAssureExtensions
    with WithE2EInstallationExampleRestAssuredUsersExtensions {

  private val simpleBatchTableScenario = ScenarioBuilder
    .streaming("SumTransactions")
    .source("sourceId", "table", "Table" -> "'transactions'".spel)
    .emptySink("end", "dead-end")

  private val designerServiceUrl = "http://localhost:8080"

  "Generate file endpoint should generate records with randomized values for scenario with table source" in {
    val expectedRecordRegex = {
      val timestampRegex                    = """\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3}""".r
      val stringRegex                       = """[a-z\d]{100}""".r
      val decimalWith15Precision2ScaleRegex = """\d{0,13}\.\d{0,2}""".r
      val recordRegex =
        s"""\\{"datetime":"$timestampRegex","client_id":"$stringRegex","amount":$decimalWith15Precision2ScaleRegex}""".r
      s"""\\{"sourceId":"sourceId","record":$recordRegex}""".r
    }
    val numberOfRecordsToGenerate = 1

    given()
      .applicationState(
        createBatchScenario(simpleBatchTableScenario.name.value)
      )
      .when()
      .request()
      .basicAuthAdmin()
      .jsonBody(toScenarioGraph(simpleBatchTableScenario).asJson.spaces2)
      .post(
        s"$designerServiceUrl/api/testInfo/${simpleBatchTableScenario.name.value}/generate/$numberOfRecordsToGenerate"
      )
      .Then()
      .statusCode(200)
      .matchPlainBodyWithRegex(expectedRecordRegex)
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
