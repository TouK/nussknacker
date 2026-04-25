package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.client

import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry._

object AzureSchemaRegistryClientFactory extends SchemaRegistryClientFactoryWithRegistration {

  override type SchemaRegistryClientT = AzureSchemaRegistryClient

  override def create(config: SchemaRegistryClientKafkaConfig): SchemaRegistryClientT = {
    new DefaultAzureSchemaRegistryClient(config)
  }

}
