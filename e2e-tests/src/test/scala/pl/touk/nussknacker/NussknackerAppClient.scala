package pl.touk.nussknacker

import better.files.File
import cats.effect.unsafe.implicits._
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentId
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.test.WithTestHttpClientCreator
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos.RunDeploymentRequest
import sttp.client3._
import sttp.model.StatusCode
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.{DecodeResult, EndpointInput, auth}
import ujson.Value

import java.util.{Base64, UUID}

class NussknackerAppClient(host: String, port: Int) {

  private val (httpClient, closeClientHandler) = {
    WithTestHttpClientCreator
      .createHttpClient()
      .allocated
      .unsafeRunSync()
  }

  def loadFlinkStreamingScenarioFromResource(scenarioName: String): Unit = {
    val resourceFileWithScenario = File(getClass.getResource(s"/scenarios/$scenarioName.json"))
    if (resourceFileWithScenario.exists) {
      loadScenario(scenarioName, resourceFileWithScenario, "Unbounded-Stream", "Flink")
    } else {
      throw new IllegalStateException(s"Cannot find resource '${resourceFileWithScenario.path.toAbsolutePath}'")
    }
  }

  def loadFlinkStreamingScenario(scenarioName: String, scenario: File): Unit = {
    loadScenario(scenarioName, scenario, "Unbounded-Stream", "Flink")
  }

  private def loadScenario(scenarioName: String, scenario: File, processingMode: String, engine: String): Unit = {
    createNewScenario(scenarioName, processingMode, "Default", engine)
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

    if (response.code != StatusCode.Ok) {
      throw new IllegalStateException(s"Cannot save scenario $scenarioName. Response: $response")
    }
  }

  def deploy() = {
    val response = SttpClientInterpreter()
      .toSecureRequest(new DeploymentApiEndpoints(authInput).runDeploymentEndpoint, Some(uri"$nuAddress"))
      .apply(AuthCredentials.PassedAuthCredentials(Base64.getEncoder.encodeToString("admin:admin".getBytes)))
      .apply(
        (
          DeploymentId(UUID.randomUUID()),
          RunDeploymentRequest(
            scenarioName = ProcessName("DetectLargeTransactions"),
            nodesDeploymentData = NodesDeploymentData(Map.empty),
            comment = None
          )
        )
      )
      .send(httpClient)

    response.body match {
      case failure: DecodeResult.Failure => ???
      case DecodeResult.Value(v) =>
        v match {
          case Left(value) => ???
          case Right(())   =>
        }
    }
  }

  def close(): Unit = {
    closeClientHandler.unsafeRunSync()
  }

  private def basicAuthRequest =
    basicRequest.auth.basic("admin", "admin")

  private lazy val nuAddress = s"http://$host:$port"

  private lazy val authInput: EndpointInput[AuthCredentials] = auth
    .basic[String](WWWAuthenticateChallenge.basic.realm("test"))
    .map(AuthCredentials.PassedAuthCredentials(_): AuthCredentials) {
      case AuthCredentials.PassedAuthCredentials(value) => value
      case other => throw new IllegalStateException(s"$other is not supported in tests")
    }

  private sealed case class ScenarioGraph(json: Value)

}
