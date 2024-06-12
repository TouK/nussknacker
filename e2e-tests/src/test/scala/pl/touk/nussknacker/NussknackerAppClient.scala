package pl.touk.nussknacker

import better.files.File
import cats.effect.unsafe.implicits._
import pl.touk.nussknacker.test.WithTestHttpClientCreator
import sttp.client3._
import sttp.model.StatusCode
import ujson.Value

class NussknackerAppClient(host: String, port: Int) {

  private val (httpClient, closeClientHandler) = {
    WithTestHttpClientCreator
      .createHttpClient()
      .allocated
      .unsafeRunSync()
  }

  def scenarioFromFile(scenario: File): Unit = {
    val scenarioName = "DetectLargeTransactions"
    createNewScenario(scenarioName, "Unbounded-Stream", "Default", "Flink")
    val scenarioGraph = importScenario(scenarioName, scenario)
    saveScenario(scenarioName, scenarioGraph)
  }

  private def createNewScenario(
      scenarioName: String,
      processingMode: String,
      category: String,
      engine: String
  ): Unit = {
    val response =
      basicAuthRequest
        .post(uri"$nuAddress/api/processes")
        .contentType("application/json")
        .body(
          s"""
             |{
             |  "name": "$scenarioName",
             |  "processingMode": "$processingMode",
             |  "category": "$category",
             |  "engineSetupName": "$engine",
             |  "isFragment": false
             |}
             |""".stripMargin
        )
        .send(httpClient)

    if (response.code != StatusCode.Created) {
      throw new IllegalStateException(s"Cannot create scenario $scenarioName. Response: $response")
    }
  }

  private def importScenario(scenarioName: String, scenario: File): ScenarioGraph = {
    val response =
      basicAuthRequest
        .post(uri"$nuAddress/api/processes/import/$scenarioName")
        .multipartBody(multipartFile("process", scenario.toJava))
        .send(httpClient)

    response.body match {
      case Right(body) if response.code == StatusCode.Ok =>
        ScenarioGraph(ujson.read(body).obj("scenarioGraph"))
      case _ =>
        throw new IllegalStateException(s"Cannot import scenario $scenarioName. Response: $response")
    }
  }

  private def saveScenario(scenarioName: String, scenarioGraph: ScenarioGraph): Unit = {
    val response =
      basicAuthRequest
        .put(uri"$nuAddress/api/processes/$scenarioName")
        .contentType("application/json")
        .body(
          s"""{
             |  "scenarioGraph": ${scenarioGraph.json.render()},
             |  "comment": ""
             |}""".stripMargin
        )
        .send(httpClient)

    if (response.code != StatusCode.Created) {
      throw new IllegalStateException(s"Cannot create scenario $scenarioName. Response: $response")
    }
  }

  def close(): Unit = {
    closeClientHandler.unsafeRunSync()
  }

  private def basicAuthRequest =
    basicRequest.auth.basic("admin", "admin")

  private lazy val nuAddress = s"http://$host:$port"

  private sealed case class ScenarioGraph(json: Value)
}
