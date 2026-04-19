package pl.touk.nussknacker.engine.schemedkafka.helpers

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.Suite
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.test.KafkaConfigProperties

trait KafkaSchemaRegistryMixin extends KafkaSpec with KafkaWithSchemaRegistryOperations { self: Suite =>

  override protected def resolveModelConfig(config: Config): Config = {
    super
      .resolveModelConfig(config)
      // schema.registry.url have to be defined even for MockSchemaRegistryClient
      .withValue(
        KafkaConfigProperties.property(kafkaComponentsConfigPrefix, "schema.registry.url"),
        fromAnyRef("not_used")
      )
      // turn off auto registration so that tests can use a mocked Schema Registry client (meaningful only in Flink tests)
      .withValue(
        s"$kafkaComponentsConfigPrefix.kafkaEspProperties.autoRegisterRecordSchemaIdSerialization",
        fromAnyRef(false)
      )
  }

}
