package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import pl.touk.nussknacker.engine.kafka.{KafkaConfig, SchemaRegistryClientKafkaConfig}

trait SchemaRegistryClientFactory extends Serializable {

  type SchemaRegistryClientT <: SchemaRegistryClient
  // TODO_PAWEL tutaj zmieniony typ
  final def create(config: KafkaConfig): Option[SchemaRegistryClientT] = create(config.schemaRegistryClientKafkaConfig)

  // TODO_PAWEL tutaj zmieniony typ
  final def create(config: SchemaRegistryClientKafkaConfig): Option[SchemaRegistryClientT] =
    // TODO_PAWEL move this property name to some const maybe? Alongside with other such
    if (config.kafkaProperties.contains("schema.registry.url")) Some(createOnConfigWithSchemaUrl(config)) else None

  // TODO_PAWEL tutaj mozna w sumie podawac ten url co go odczytalismy i go nie czytac z configa
  // tak powinno byc, koniecznie
  protected def createOnConfigWithSchemaUrl(config: SchemaRegistryClientKafkaConfig): SchemaRegistryClientT
}

trait SchemaRegistryClientFactoryWithRegistration extends SchemaRegistryClientFactory {

  override type SchemaRegistryClientT <: SchemaRegistryClientWithRegistration

}
