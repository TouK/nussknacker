package pl.touk.nussknacker.engine.kafka

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.test.{AvailablePortFinder, KafkaConfigProperties, WithModelConfig}

trait KafkaSpec extends BeforeAndAfterAll with WithModelConfig { self: Suite =>

  var kafkaServer: EmbeddedKafkaKraftServer = _
  var kafkaClient: KafkaClient              = _
  val kafkaBrokerConfig                     = Map.empty[String, String]

  override protected def resolveModelConfig(config: Config): Config =
    super
      .resolveModelConfig(config)
      .withValue(
        KafkaConfigProperties.bootstrapServersProperty("components.kafka.config"),
        fromAnyRef(kafkaServer.bootstrapServers)
      )
      // For tests we want to read from the beginning...
      .withValue(KafkaConfigProperties.property("components.kafka.config", "auto.offset.reset"), fromAnyRef("earliest"))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    AvailablePortFinder.withAvailablePortsBlocked(2) {
      case List(controllerPort, brokerPort) =>
        kafkaServer = EmbeddedKafkaKraftServer.run(
          brokerPort = brokerPort,
          controllerPort = controllerPort,
          kafkaBrokerConfig = kafkaBrokerConfig
        )
      case _ => throw new MatchError(())
    }
    kafkaClient = new KafkaClient(kafkaAddress = kafkaServer.bootstrapServers, self.suiteName)
  }

  override protected def afterAll(): Unit = {
    try {
      kafkaClient.shutdown()
      kafkaServer.shutdownKafkaServer()
    } finally {
      super.afterAll()
    }
  }

}
