package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers._
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.kafka.KafkaZookeeperUtils.richConsumer
import pl.touk.nussknacker.test.PatientScalaFutures

class NuKafkaRuntimeDockerJsonTest extends FunSuite with BaseNuKafkaRuntimeDockerTest with Matchers with PatientScalaFutures with LazyLogging {

  override val container: Container = {
    kafkaContainer.start() // must be started before prepareTestCaseFixture because it creates topic via api
    fixture = prepareTestCaseFixture("json-ping-pong", NuKafkaRuntimeTestSamples.jsonPingPongScenario)
    val runtimeContainer = prepareRuntimeContainer(fixture.scenarioFile)
    MultipleContainers(kafkaContainer, runtimeContainer)
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