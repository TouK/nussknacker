package pl.touk.nussknacker.engine.kafka

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}

class KafkaConfigSpec extends FunSuite with Matchers {

  test("parse config") {
    val typesafeConfig = ConfigFactory.parseString(
      """kafka {
        |  kafkaAddress: "localhost:9092"
        |  kafkaProperties {
        |    "auto.offset.reset": latest
        |  }
        |}""".stripMargin)
    val expectedConfig = KafkaConfig(
      "localhost:9092",
      Some(Map("auto.offset.reset" -> "latest")),
      None, None)
    KafkaConfig.parseConfig(typesafeConfig) shouldEqual expectedConfig
  }

}
