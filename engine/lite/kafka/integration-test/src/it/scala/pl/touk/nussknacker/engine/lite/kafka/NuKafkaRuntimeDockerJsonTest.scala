package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers._
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.lite.kafka.sample.NuKafkaRuntimeTestSamples
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.test.PatientScalaFutures

class NuKafkaRuntimeDockerJsonTest extends AnyFunSuite with BaseNuKafkaRuntimeDockerTest with Matchers with PatientScalaFutures with LazyLogging {

  override val container: Container = {
    kafkaContainer.start() // must be started before prepareTestCaseFixture because it creates topic via api
    schemaRegistryContainer.start() // should be started after kafka
    fixture = prepareTestCaseFixture(NuKafkaRuntimeTestSamples.pingPongScenarioId, NuKafkaRuntimeTestSamples.pingPongScenario)
    registerSchemas()
    startRuntimeContainer(fixture.scenarioFile, additionalEnvs = Map("SCHEMA_REGISTRY_URL" -> dockerNetworkSchemaRegistryAddress))
    MultipleContainers(kafkaContainer, schemaRegistryContainer, runtimeContainer)
  }

  private def registerSchemas(): Unit = {
    schemaRegistryClient.register(ConfluentUtils.valueSubject(fixture.inputTopic), NuKafkaRuntimeTestSamples.jsonPingSchema)
    schemaRegistryClient.register(ConfluentUtils.valueSubject(fixture.outputTopic), NuKafkaRuntimeTestSamples.jsonPingSchema)
  }

  test("json ping-pong should work") {
    kafkaClient.sendMessage(fixture.inputTopic, NuKafkaRuntimeTestSamples.jsonPingMessage).futureValue
    try {
      val messages = kafkaClient.createConsumer().consume(fixture.outputTopic, secondsToWait = 60).take(1).map(rec => new String(rec.message())).toList
      messages shouldBe List(NuKafkaRuntimeTestSamples.jsonPingMessage)
    } finally {
      consumeFirstError shouldBe empty
    }
  }

}