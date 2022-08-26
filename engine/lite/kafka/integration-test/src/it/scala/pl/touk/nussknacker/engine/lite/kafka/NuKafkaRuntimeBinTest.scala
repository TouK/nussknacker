package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers.{Container, MultipleContainers}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.lite.kafka.sample.NuKafkaRuntimeTestSamples
import pl.touk.nussknacker.engine.lite.utils.{BaseNuRuntimeBinTestMixin, NuRuntimeTestUtils}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils

// depends on liteEngineKafkaRuntime / Universal / stage sbt task
class NuKafkaRuntimeBinTest extends AnyFunSuite with BaseNuRuntimeBinTestMixin with BaseNuKafkaRuntimeDockerTest with LazyLogging {

  test("should run scenario and pass data to output ") {
    val shellScriptArgs = Array(shellScriptPath.toString, fixture.scenarioFile.toString, NuRuntimeTestUtils.deploymentDataFile.toString)
    val shellScriptEnvs = Array(
      s"KAFKA_ADDRESS=$kafkaBoostrapServer",
      "KAFKA_AUTO_OFFSET_RESET=earliest",
      s"SCHEMA_REGISTRY_URL=$mappedSchemaRegistryAddress",
      // random management port to avoid clashing of ports
      "CONFIG_FORCE_akka_management_http_port=0",
      "CONFIG_FORCE_kafka_lowLevelComponentsEnabled=false",
      // It looks like github-actions doesn't look binding to 0.0.0.0, was problems like: Bind failed for TCP channel on endpoint [/10.1.0.183:0]
      "CONFIG_FORCE_akka_management_http_hostname=127.0.0.1",
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

  override val container: Container = {
    kafkaContainer.start() // must be started before prepareTestCaseFixture because it creates topic via api
    schemaRegistryContainer.start() // should be started after kafka
    fixture = prepareTestCaseFixture(NuKafkaRuntimeTestSamples.pingPongScenarioId, NuKafkaRuntimeTestSamples.pingPongScenario)
    registerSchemas()
    MultipleContainers(kafkaContainer, schemaRegistryContainer)
  }

  private def registerSchemas(): Unit = {
    schemaRegistryClient.register(ConfluentUtils.valueSubject(fixture.inputTopic), NuKafkaRuntimeTestSamples.jsonPingSchema)
    schemaRegistryClient.register(ConfluentUtils.valueSubject(fixture.outputTopic), NuKafkaRuntimeTestSamples.jsonPingSchema)
  }
}