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
import io.circe.parser._

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.util.Try

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
          |    "name" : "SumTransactions",
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
      .post(s"http://localhost:8080/api/testInfo/SumTransactions/generate/10")
      .Then()
      .statusCode(200)
      .extract()
      .body()
      .asString()

    val firstLine  = testResultContent.split('\n').head
    val testRecord = parse(firstLine).getOrElse(fail).asObject.get.toMap("record").asObject.get.toMap

    testRecord("client_id").isString shouldBe true
    testRecord("date").isString shouldBe true
    testRecord("amount").isNumber shouldBe true
    isParseableAsLocalDateTime(testRecord("datetime").asString.get) shouldBe true
  }

  private def isParseableAsLocalDateTime(str: String) = {
    val flinkTimestampJsonPattern = "yyyy-MM-dd HH:mm:ss.SSS"
    Try(LocalDateTime.parse(str, DateTimeFormatter.ofPattern(flinkTimestampJsonPattern))).isSuccess
  }

  private lazy val simpleBatchTableScenario = ScenarioBuilder
    .streaming("SumTransactions")
    .source("sourceId", "table", "Table" -> Expression.spel("'transactions'"))
    .emptySink("end", "dead-end")

}
