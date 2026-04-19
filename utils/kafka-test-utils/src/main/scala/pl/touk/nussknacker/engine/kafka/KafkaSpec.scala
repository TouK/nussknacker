package pl.touk.nussknacker.engine.kafka

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.test.{AvailablePortFinder, KafkaConfigProperties, WithModelConfig}

trait KafkaSpec extends BeforeAndAfterAll with WithModelConfig { self: Suite =>

  var kafkaServer: EmbeddedKafkaKraftServer = _
  var kafkaClient: KafkaClient              = _
  val kafkaBrokerConfig                     = Map.empty[String, String]

  protected def kafkaComponentsConfigPrefix: String

  override protected def resolveModelConfig(config: Config): Config =
    super
      .resolveModelConfig(config)
      .withValue(
        KafkaConfigProperties.bootstrapServersProperty(kafkaComponentsConfigPrefix),
        fromAnyRef(kafkaServer.bootstrapServers)
      )
      // For tests we want to read from the beginning...
      .withValue(
        KafkaConfigProperties.property(kafkaComponentsConfigPrefix, "auto.offset.reset"),
        fromAnyRef("earliest")
      )
      // Use explicit schemaRegistryId (meaningful only in Flink tests)
      .withValue(s"$kafkaComponentsConfigPrefix.optimizedGenericRecordSerialization.schemaRegistryId", fromAnyRef(1771))

  protected final lazy val kafkaComponentsConfig: KafkaComponentsConfig =
    KafkaComponentsConfig.parseConfig(modelConfig.getConfig(kafkaComponentsConfigPrefix))

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
