package pl.touk.nussknacker.engine.lite.requestresponse

import com.dimafeng.testcontainers.{Container, ForAllTestContainer, GenericContainer}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.lite.requestresponse.sample.NuReqRespTestSamples.{
  jsonPingMessage,
  jsonPongMessage,
  pingPongScenario
}
import pl.touk.nussknacker.engine.lite.utils.NuRuntimeDockerTestUtils._
import pl.touk.nussknacker.engine.lite.utils.NuRuntimeTestUtils
import pl.touk.nussknacker.engine.lite.utils.NuRuntimeTestUtils.testCaseId
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, VeryPatientScalaFutures}
import sttp.client3.{basicRequest, HttpURLConnectionBackend, Identity, SttpBackend, UriContext}

class NuReqRespRuntimeDockerTest
    extends AnyFunSuite
    with ForAllTestContainer
    with Matchers
    with VeryPatientScalaFutures
    with LazyLogging
    with EitherValuesDetailedMessage {

  private implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

  protected var runtimeContainer: GenericContainer = null

  override def container: Container = {
    if (runtimeContainer == null) {
      runtimeContainer = startRuntimeContainer(
        NuRuntimeTestUtils.saveScenarioToTmp(pingPongScenario, testCaseId(suiteName, pingPongScenario)),
        logger.underlying
      )
    }
    runtimeContainer
  }

  protected def mappedRuntimeApiPort: Int = runtimeContainer.mappedPort(runtimeApiPort)

  test("docker image should handle ping pong via http") {
    val host    = runtimeContainer.host
    val request = basicRequest.post(uri"http://$host".port(mappedRuntimeApiPort))
    request.body(jsonPingMessage("dockerFoo")).send(backend).body shouldBe Right(jsonPongMessage("dockerFoo"))
  }

  test("should get scenario definition via http") {
    val definitionReq =
      basicRequest.get(uri"http://${runtimeContainer.host}".port(mappedRuntimeApiPort).withPath("definition"))

    val definition = definitionReq.send(backend).body.rightValue

    definition should include("\"openapi\"")
  }

}
