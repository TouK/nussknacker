package pl.touk.nussknacker.engine.schemedkafka.helpers

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec}
import pl.touk.nussknacker.test.{KafkaConfigProperties, WithModelConfig}

trait SchemaRegistryMixin
    extends AnyFunSuite
    with KafkaSpec
    with KafkaWithSchemaRegistryOperations
    with WithModelConfig {

  override protected def resolveModelConfig(config: Config): Config = {
    super
      .resolveModelConfig(config)
      .withValue(KafkaConfigProperties.bootstrapServersProperty(), fromAnyRef(kafkaServer.bootstrapServers))
      // schema.registry.url have to be defined even for MockSchemaRegistryClient
      .withValue(KafkaConfigProperties.property("schema.registry.url"), fromAnyRef("not_used"))
      // we turn off auto registration to do it on our own passing mocked schema registry client // meaningful only in Flink tests
      .withValue(s"kafka.kafkaEspProperties.autoRegisterRecordSchemaIdSerialization", fromAnyRef(false))
  }

  protected lazy val testModelConfig: ModelConfig = ModelConfig.parse(modelConfig)

  protected lazy val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(modelConfig)

}
