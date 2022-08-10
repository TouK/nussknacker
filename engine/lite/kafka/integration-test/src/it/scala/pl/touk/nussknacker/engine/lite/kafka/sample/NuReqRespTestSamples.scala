package pl.touk.nussknacker.engine.lite.kafka.sample

import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

object NuReqRespTestSamples {

  import pl.touk.nussknacker.engine.spel.Implicits._

  private val pingSchema = """{
                             |  "type": "object",
                             |  "properties": {
                             |    "ping": { "type": "string" }
                             |  }
                             |}
                             |""".stripMargin

  private val pongSchema = """{
                             |  "type": "object",
                             |  "properties": {
                             |    "pong": { "type": "string" }
                             |  }
                             |}
                             |""".stripMargin

  val pingPongScenario: CanonicalProcess = ScenarioBuilder
    .requestResponse("reqresp-ping-pong") // TODO: test defined path case
    .additionalFields(properties = Map(
      "inputSchema" -> pingSchema,
      "outputSchema" -> pongSchema
    ))
    .source("source", "request")
    .emptySink("sink", "response", "pong" -> "#input.ping")
    .toCanonicalProcess

  def jsonPingMessage(msg: String) = s"""{"ping":"$msg"}"""
  def jsonPongMessage(msg: String) = s"""{"pong":"$msg"}"""

}
