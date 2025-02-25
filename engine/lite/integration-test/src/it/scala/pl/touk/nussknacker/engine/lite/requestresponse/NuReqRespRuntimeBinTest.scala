package pl.touk.nussknacker.engine.lite.requestresponse

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.engine.lite.requestresponse.sample.NuReqRespTestSamples.{
  jsonPingMessage,
  jsonPongMessage,
  pingPongScenario
}
import pl.touk.nussknacker.engine.lite.utils.NuRuntimeTestUtils.{saveScenarioToTmp, testCaseId}
import pl.touk.nussknacker.engine.lite.utils.{BaseNuRuntimeBinTestMixin, NuRuntimeTestUtils}
import pl.touk.nussknacker.test.AvailablePortFinder
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend, UriContext, basicRequest}
import sttp.model.StatusCode

// depends on liteEngineRuntimeApp / Universal / stage sbt task
class NuReqRespRuntimeBinTest extends AnyFunSuite with BaseNuRuntimeBinTestMixin with LazyLogging {

  private implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

  test("binary version should handle ping pong via http") {
    val shellScriptArgs = Array(
      shellScriptPath.toString,
      saveScenarioToTmp(pingPongScenario, testCaseId(suiteName, pingPongScenario)).toString,
      NuRuntimeTestUtils.deploymentDataFile.toString
    )
    val port = AvailablePortFinder.findAvailablePorts(1).head
    val shellScriptEnvs = Array(
      "CONFIG_FORCE_http_interface=localhost",
      s"CONFIG_FORCE_http_port=$port",
    ) ++ pekkoManagementEnvs

    withProcessExecutedInBackground(
      shellScriptArgs,
      shellScriptEnvs,
      {}, {
        eventually {
          basicRequest
            .get(uri"http://localhost".port(port).withPath("alive"))
            .send(backend)
            .code shouldEqual StatusCode.Ok
        }
        val request = basicRequest.post(uri"http://localhost".port(port))
        request.body(jsonPingMessage("foo")).send(backend).body shouldBe Right(jsonPongMessage("foo"))
      }
    )
  }

}
