package pl.touk.nussknacker.engine.kafka

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class KafkaConfigSpec extends AnyFunSuite with Matchers {

  test("parse config") {
    val typesafeConfig = ConfigFactory.parseString("""kafka {
        |  kafkaProperties {
        |    "bootstrap.servers": "localhost:9092"
        |    "auto.offset.reset": latest
        |  }
        |}""".stripMargin)
    val expectedConfig =
      KafkaConfig(Some(Map("bootstrap.servers" -> "localhost:9092", "auto.offset.reset" -> "latest")), None, None)
    KafkaConfig.parseConfig(typesafeConfig) shouldEqual expectedConfig
  }

  test("parse legacy config") {
    val typesafeConfig = ConfigFactory.parseString("""kafka {
        |  kafkaAddress: "localhost:9092"
        |  kafkaProperties {
        |    "auto.offset.reset": latest
        |  }
        |}""".stripMargin)
    val expectedConfig =
      KafkaConfig(Some(Map("auto.offset.reset" -> "latest")), None, None, kafkaAddress = Some("localhost:9092"))
    KafkaConfig.parseConfig(typesafeConfig) shouldEqual expectedConfig
  }

  test("parse config with topicExistenceValidation") {
    val typesafeConfig = ConfigFactory.parseString("""kafka {
        |  kafkaProperties {
        |    "bootstrap.servers": "localhost:9092"
        |    "auto.offset.reset": latest
        |  }
        |  topicsExistenceValidationConfig: {
        |     enabled: true
        |  }
        |}""".stripMargin)
    val expectedConfig = KafkaConfig(
      kafkaProperties = Some(Map("bootstrap.servers" -> "localhost:9092", "auto.offset.reset" -> "latest")),
      kafkaEspProperties = None,
      consumerGroupNamingStrategy = None,
      avroKryoGenericRecordSchemaIdSerialization = None,
      topicsExistenceValidationConfig = TopicsExistenceValidationConfig(enabled = true)
    )
    KafkaConfig.parseConfig(typesafeConfig) shouldEqual expectedConfig
  }

}
