package pl.touk.nussknacker.engine.kafka

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class KafkaComponentsConfigSpec extends AnyFunSuite with Matchers {

  test("parse config") {
    val typesafeConfig = ConfigFactory.parseString("""{
        |  kafkaProperties {
        |    "bootstrap.servers": "localhost:9092"
        |    "auto.offset.reset": latest
        |  }
        |}""".stripMargin)
    val expectedConfig =
      KafkaComponentsConfig(Map("bootstrap.servers" -> "localhost:9092", "auto.offset.reset" -> "latest"), None, None)
    KafkaComponentsConfig.parseConfig(typesafeConfig) shouldEqual expectedConfig
  }

  test("parse config with topicExistenceValidation") {
    val typesafeConfig = ConfigFactory.parseString("""{
        |  kafkaProperties {
        |    "bootstrap.servers": "localhost:9092"
        |    "auto.offset.reset": latest
        |  }
        |  topicsExistenceValidationConfig: {
        |     enabled: true
        |  }
        |}""".stripMargin)
    val expectedConfig = KafkaComponentsConfig(
      kafkaProperties = Map("bootstrap.servers" -> "localhost:9092", "auto.offset.reset" -> "latest"),
      kafkaEspProperties = None,
      consumerGroupNamingStrategy = None,
      topicsExistenceValidationConfig = TopicsExistenceValidationConfig(enabled = true)
    )
    KafkaComponentsConfig.parseConfig(typesafeConfig) shouldEqual expectedConfig
  }

}
