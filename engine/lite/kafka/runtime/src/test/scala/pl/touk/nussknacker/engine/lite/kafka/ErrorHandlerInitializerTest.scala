package pl.touk.nussknacker.engine.lite.kafka

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.EnumerationReader._
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.lite.kafka.KafkaTransactionalScenarioInterpreter.{EngineConfig, _}
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.util.Random


class ErrorHandlerInitializerTest extends FunSuite with KafkaSpec with Matchers with PatientScalaFutures {

  private def initializer(topic: String, createIfNotExists: Boolean): ErrorHandlerInitializer = {
    val engineConfig = config
      .withValue("exceptionHandlingConfig.topic", fromAnyRef(topic))
      .withValue("exceptionHandlingConfig.createTopicIfNotExists", fromAnyRef(createIfNotExists))
      .as[EngineConfig]
    new ErrorHandlerInitializer(engineConfig.kafka, engineConfig.exceptionHandlingConfig)
  }

  test("should create topic if not exists") {
    val name = s"topic-${Random.nextInt()}"

    kafkaClient.topic(name) shouldBe 'empty
    initializer(name, createIfNotExists = true).init()
    kafkaClient.topic(name) shouldBe 'defined
  }

  test("should do nothing if topic already exists") {
    val name = s"topic-${Random.nextInt()}"
    kafkaClient.createTopic(name, partitions = 10)

    kafkaClient.topic(name) shouldBe 'defined
    initializer(name, createIfNotExists = true).init()
    kafkaClient.topic(name).map(_.partitions().size()) shouldBe Some(10)

    initializer(name, createIfNotExists = false).init()
    kafkaClient.topic(name).map(_.partitions().size()) shouldBe Some(10)

  }

  test("should fail if topic not exists and configured not to create") {
    val name = s"topic-${Random.nextInt()}"

    kafkaClient.topic(name) shouldBe 'empty
    intercept[IllegalArgumentException] {
      initializer(name, createIfNotExists = false).init()
    }.getMessage shouldBe s"Errors topic: $name does not exist, 'createTopicIfNotExists' is 'false', please create the topic"
  }

}
