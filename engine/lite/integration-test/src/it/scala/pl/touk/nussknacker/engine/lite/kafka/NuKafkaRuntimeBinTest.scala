package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers.{Container, MultipleContainers}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.lite.kafka.sample.NuKafkaRuntimeTestSamples
import pl.touk.nussknacker.engine.lite.utils.{BaseNuRuntimeBinTestMixin, NuRuntimeTestUtils}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils

// depends on liteEngineRuntimeApp / Universal / stage sbt task
class NuKafkaRuntimeBinTest
    extends AnyFunSuite
    with BaseNuRuntimeBinTestMixin
    with BaseNuKafkaRuntimeDockerTest
    with LazyLogging {

  test("should run scenario and pass data to output ") {
    val shellScriptArgs =
      Array(shellScriptPath.toString, fixture.scenarioFile.toString, NuRuntimeTestUtils.deploymentDataFile.toString)
    val shellScriptEnvs = Array(
      s"KAFKA_ADDRESS=$kafkaBoostrapServer",
      "KAFKA_AUTO_OFFSET_RESET=earliest",
      s"SCHEMA_REGISTRY_URL=$mappedSchemaRegistryAddress",
      // random management port to avoid clashing of ports
      "CONFIG_FORCE_pekko_management_http_port=0",
      // It looks like github-actions doesn't look binding to 0.0.0.0, was problems like: Bind failed for TCP channel on endpoint [/10.1.0.183:0]
      "CONFIG_FORCE_pekko_management_http_hostname=127.0.0.1",
      "KAFKA_AUTO_OFFSET_RESET=earliest"
    ) ++ pekkoManagementEnvs
    withProcessExecutedInBackground(
      shellScriptArgs,
      shellScriptEnvs, {
        kafkaClient.sendMessage(fixture.inputTopic.name, NuKafkaRuntimeTestSamples.jsonPingMessage).futureValue
      }, {
        val messages =
          kafkaClient.createConsumer().consumeWithJson[String](fixture.outputTopic.name).take(1).head.message()
        messages shouldBe NuKafkaRuntimeTestSamples.jsonPingMessage
      }
    )
  }

  override val container: Container = {
    kafkaContainer.start()          // must be started before prepareTestCaseFixture because it creates topic via api
    schemaRegistryContainer.start() // should be started after kafka
    fixture = prepareTestCaseFixture(
      NuKafkaRuntimeTestSamples.pingPongScenarioName,
      NuKafkaRuntimeTestSamples.pingPongScenario
    )
    registerSchemas()
    MultipleContainers(kafkaContainer, schemaRegistryContainer)
  }

  private def registerSchemas(): Unit = {
    schemaRegistryClient.register(
      ConfluentUtils.valueSubject(fixture.inputTopic.toUnspecialized),
      NuKafkaRuntimeTestSamples.jsonPingSchema
    )
    schemaRegistryClient.register(
      ConfluentUtils.valueSubject(fixture.outputTopic.toUnspecialized),
      NuKafkaRuntimeTestSamples.jsonPingSchema
    )
  }

}
