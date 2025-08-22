package pl.touk.nussknacker.engine.schemedkafka.helpers

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.kafka.{KafkaComponentsConfig, KafkaSpec}
import pl.touk.nussknacker.test.{KafkaConfigProperties, WithModelConfig}

trait SchemaRegistryMixin
    extends AnyFunSuite
    with KafkaSpec
    with KafkaWithSchemaRegistryOperations
    with WithModelConfig {

  override protected def resolveModelConfig(config: Config): Config = {
    super
      .resolveModelConfig(config)
      .withValue(
        KafkaConfigProperties.bootstrapServersProperty(kafkaComponentsConfigPrefix),
        fromAnyRef(kafkaServer.bootstrapServers)
      )
      // schema.registry.url have to be defined even for MockSchemaRegistryClient
      .withValue(
        KafkaConfigProperties.property(kafkaComponentsConfigPrefix, "schema.registry.url"),
        fromAnyRef("not_used")
      )
      // we turn off auto registration to do it on our own passing mocked schema registry client // meaningful only in Flink tests
      .withValue(
        s"$kafkaComponentsConfigPrefix.kafkaEspProperties.autoRegisterRecordSchemaIdSerialization",
        fromAnyRef(false)
      )
  }

  protected lazy val kafkaComponentsConfig: KafkaComponentsConfig =
    KafkaComponentsConfig.parseConfig(modelConfig.getConfig(kafkaComponentsConfigPrefix))

  protected def namingStrategy: NamingStrategy = NamingStrategy.Disabled

}
