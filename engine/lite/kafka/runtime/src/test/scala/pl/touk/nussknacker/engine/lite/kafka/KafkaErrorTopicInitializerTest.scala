package pl.touk.nussknacker.engine.lite.kafka

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.EnumerationReader._
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.kafka.exception.KafkaErrorTopicInitializer
import pl.touk.nussknacker.engine.lite.kafka.KafkaTransactionalScenarioInterpreter._
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.util.Random


class KafkaErrorTopicInitializerTest extends FunSuite with KafkaSpec with Matchers with PatientScalaFutures {

  private def initializer(topic: String): KafkaErrorTopicInitializer = {
    val engineConfig = config
      .withValue("exceptionHandlingConfig.topic", fromAnyRef(topic))
      .as[EngineConfig]
    new KafkaErrorTopicInitializer(engineConfig.kafka, engineConfig.exceptionHandlingConfig)
  }

  test("should create topic if not exists") {
    val name = s"topic-${Random.nextInt()}"

    kafkaClient.topic(name) shouldBe 'empty
    initializer(name).init()
    kafkaClient.topic(name) shouldBe 'defined
  }

  test("should do nothing if topic already exists") {
    val name = s"topic-${Random.nextInt()}"
    kafkaClient.createTopic(name, partitions = 10)

    kafkaClient.topic(name) shouldBe 'defined
    initializer(name).init()
    kafkaClient.topic(name).map(_.partitions().size()) shouldBe Some(10)


  }

}
