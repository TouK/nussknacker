package pl.touk.nussknacker.engine.lite.kafka

import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax._
import org.apache.commons.io.FileUtils
import org.scalatest.FunSuite
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.test.AvailablePortFinder
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend, UriContext, basicRequest}

import java.io.File
import java.nio.charset.StandardCharsets

class NuReqRespRuntimeBinTest extends FunSuite with BaseNuRuntimeBinTestMixin with LazyLogging {

  private implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()

  test("should ping pong using http ") {
    val inputSchema = """{
                        |  "type": "object",
                        |  "properties": {
                        |    "ping": { "type": "string" }
                        |  }
                        |}
                        |""".stripMargin
    val outputSchema = """{
                         |  "type": "object",
                         |  "properties": {
                         |    "pong": { "type": "string" }
                         |  }
                         |}
                         |""".stripMargin

    val scenario = ScenarioBuilder
      .requestResponse("reqresp-ping-pong") // TODO: test defined path case
      .additionalFields(properties = Map(
        "inputSchema" -> inputSchema,
        "outputSchema" -> outputSchema
      ))
      .source("source", "request")
      .emptySink("sink", "response", "pong" -> "#input.ping")
      .toCanonicalProcess
    val deploymentDataFile = new File(getClass.getResource("/sampleDeploymentData.conf").getFile)

    val shellScriptArgs = Array(shellScriptPath.toString, saveScenarioToTmp(scenario).toString, deploymentDataFile.toString)
    val port = AvailablePortFinder.findAvailablePorts(1).head
    val shellScriptEnvs = Array(
      "CONFIG_FORCE_http_interface=localhost",
      s"CONFIG_FORCE_http_port=$port",
    ) ++ akkaManagementEnvs

    withProcessExecutedInBackground(shellScriptArgs, shellScriptEnvs, {}, {
      eventually { // TODO: check ready probe
        val request = basicRequest.post(uri"http://localhost".port(port).path("scenario", scenario.id))
        request.body("""{ "ping": "foo" }""").send().body shouldBe Right("""{"pong":"foo"}""")
      }
    })
  }

  private def saveScenarioToTmp(scenario: CanonicalProcess): File = {
    val scenarioFilePrefix = suiteName + "-" + scenario.id
    val jsonFile = File.createTempFile(scenarioFilePrefix, ".json")
    jsonFile.deleteOnExit()
    FileUtils.write(jsonFile, scenario.asJson.spaces2, StandardCharsets.UTF_8)
    jsonFile
  }

}
