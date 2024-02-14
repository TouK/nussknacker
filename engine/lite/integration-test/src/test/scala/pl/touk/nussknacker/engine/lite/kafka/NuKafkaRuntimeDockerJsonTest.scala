package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers._
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import pl.touk.nussknacker.engine.lite.kafka.sample.NuKafkaRuntimeTestSamples
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.test.PatientScalaFutures

@Slow
class NuKafkaRuntimeDockerJsonTest
    extends AnyFunSuite
    with BaseNuKafkaRuntimeDockerTest
    with Matchers
    with PatientScalaFutures
    with LazyLogging {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer

  override val container: Container = {
    kafkaContainer.start()          // must be started before prepareTestCaseFixture because it creates topic via api
    schemaRegistryContainer.start() // should be started after kafka
    fixture =
      prepareTestCaseFixture(NuKafkaRuntimeTestSamples.pingPongScenarioName, NuKafkaRuntimeTestSamples.pingPongScenario)
    registerSchemas()
    startRuntimeContainer(fixture.scenarioFile)
    MultipleContainers(kafkaContainer, schemaRegistryContainer, runtimeContainer)
  }

  private def registerSchemas(): Unit = {
    schemaRegistryClient.register(
      ConfluentUtils.valueSubject(fixture.inputTopic),
      NuKafkaRuntimeTestSamples.jsonPingSchema
    )
    schemaRegistryClient.register(
      ConfluentUtils.valueSubject(fixture.outputTopic),
      NuKafkaRuntimeTestSamples.jsonPingSchema
    )
  }

  test("json ping-pong should work") {
    kafkaClient.sendMessage(fixture.inputTopic, NuKafkaRuntimeTestSamples.jsonPingMessage).futureValue
    try {
      val message = kafkaClient.createConsumer().consumeWithJson[String](fixture.outputTopic).head.message()
      message shouldBe NuKafkaRuntimeTestSamples.jsonPingMessage
    } finally {
      consumeFirstError shouldBe empty
    }
  }

}
