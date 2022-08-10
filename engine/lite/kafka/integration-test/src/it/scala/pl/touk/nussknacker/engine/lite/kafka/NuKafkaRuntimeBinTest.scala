package pl.touk.nussknacker.engine.lite.kafka

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.FunSuite
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.lite.kafka.sample.NuKafkaRuntimeTestSamples

// depends on liteEngineKafkaRuntime / Universal / stage sbt task
class NuKafkaRuntimeBinTest extends FunSuite with BaseNuRuntimeBinTestMixin with KafkaSpec with NuKafkaRuntimeTestMixin with LazyLogging {

  override protected def kafkaBoostrapServer: String = kafkaServer.kafkaAddress

  test("should run scenario and pass data to output ") {
    val fixture = prepareTestCaseFixture("json-ping-pong", NuKafkaRuntimeTestSamples.jsonPingPongScenario)

    val shellScriptArgs = Array(shellScriptPath.toString, fixture.scenarioFile.toString, NuRuntimeTestUtils.deploymentDataFile.toString)
    val shellScriptEnvs = Array(
      s"KAFKA_ADDRESS=$kafkaBoostrapServer",
      "KAFKA_AUTO_OFFSET_RESET=earliest") ++ akkaManagementEnvs
    withProcessExecutedInBackground(shellScriptArgs, shellScriptEnvs,
      {
        kafkaClient.sendMessage(fixture.inputTopic, NuKafkaRuntimeTestSamples.jsonPingMessage).futureValue
      },
      {
        val messages = kafkaClient.createConsumer().consume(fixture.outputTopic, secondsToWait = 60).take(1).map(rec => new String(rec.message())).toList
        messages shouldBe List(NuKafkaRuntimeTestSamples.jsonPingMessage)
      })
  }

}