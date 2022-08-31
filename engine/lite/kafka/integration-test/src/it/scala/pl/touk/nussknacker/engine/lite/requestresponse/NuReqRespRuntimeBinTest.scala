package pl.touk.nussknacker.engine.lite.requestresponse

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.engine.lite.requestresponse.sample.NuReqRespTestSamples.{jsonPingMessage, jsonPongMessage, pingPongScenario}
import pl.touk.nussknacker.engine.lite.utils.NuRuntimeTestUtils.{saveScenarioToTmp, testCaseId}
import pl.touk.nussknacker.engine.lite.utils.{BaseNuRuntimeBinTestMixin, NuRuntimeTestUtils}
import pl.touk.nussknacker.test.AvailablePortFinder
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend, UriContext, basicRequest}
import sttp.model.StatusCode

// depends on liteEngineKafkaRuntime / Universal / stage sbt task
class NuReqRespRuntimeBinTest extends AnyFunSuite with BaseNuRuntimeBinTestMixin with LazyLogging {

  private implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()

  test("binary version should handle ping pong via http") {
    val shellScriptArgs = Array(shellScriptPath.toString, saveScenarioToTmp(pingPongScenario, testCaseId(suiteName, pingPongScenario)).toString, NuRuntimeTestUtils.deploymentDataFile.toString)
    val port = AvailablePortFinder.findAvailablePorts(1).head
    val shellScriptEnvs = Array(
      "CONFIG_FORCE_http_interface=localhost",
      s"CONFIG_FORCE_http_port=$port",
    ) ++ akkaManagementEnvs

    withProcessExecutedInBackground(shellScriptArgs, shellScriptEnvs, {}, {
      eventually {
        basicRequest.get(uri"http://localhost".port(port).path("alive")).send().code shouldEqual StatusCode.Ok
      }
      val request = basicRequest.post(uri"http://localhost".port(port))
      request.body(jsonPingMessage("foo")).send().body shouldBe Right(jsonPongMessage("foo"))
    })
  }

}
