package pl.touk.nussknacker.engine.kafka

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.Config
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.test.{AvailablePortFinder, KafkaConfigProperties, WithConfig}

trait KafkaSpec extends BeforeAndAfterAll with WithConfig { self: Suite =>

  var kafkaServerWithDependencies: EmbeddedKafkaServerWithDependencies = _
  var kafkaClient: KafkaClient                                         = _
  val kafkaBrokerConfig                                                = Map.empty[String, String]

  override protected def resolveConfig(config: Config): Config =
    super
      .resolveConfig(config)
      .withValue(KafkaConfigProperties.bootstrapServersProperty(), fromAnyRef(kafkaServerWithDependencies.kafkaAddress))
      // For tests we want to read from the beginning...
      .withValue(KafkaConfigProperties.property("auto.offset.reset"), fromAnyRef("earliest"))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    AvailablePortFinder.withAvailablePortsBlocked(2) {
      case List(controllerPort, brokerPort) =>
        kafkaServerWithDependencies = EmbeddedKafkaServerWithDependencies.run(
          brokerPort = brokerPort,
          controllerPort = controllerPort,
          kafkaBrokerConfig = kafkaBrokerConfig
        )
      case _ => throw new MatchError(())
    }
    kafkaClient = new KafkaClient(kafkaAddress = kafkaServerWithDependencies.kafkaAddress, self.suiteName)
  }

  override protected def afterAll(): Unit = {
    try {
      kafkaClient.shutdown()
      kafkaServerWithDependencies.shutdownKafkaServerAndDependencies()
    } finally {
      super.afterAll()
    }
  }

}
