package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import com.azure.data.schemaregistry.SchemaRegistryVersion
import com.azure.data.schemaregistry.implementation.{AzureSchemaRegistryImpl, AzureSchemaRegistryImplBuilder}
import pl.touk.nussknacker.engine.kafka.SchemaRegistryClientKafkaConfig

// This is copy-paste of SchemaRegistryClientBuilder.buildAsyncClient
// We need it because we want to keep our enhanced SchemaRegistry client as much compatible
// with this used by Avro(De)Serializer as possible.
object AzureSchemaRegistryImplFactory {

  def create(config: SchemaRegistryClientKafkaConfig): AzureSchemaRegistryImpl = {
    val httpPipeline = AzureHttpPipelineFactory.createPipeline(config)
    val fullyQualifiedNamespace = config.kafkaProperties("schema.registry.url")

    new AzureSchemaRegistryImplBuilder()
      .serializerAdapter(SchemaRegistryJsonSerializer)
      .endpoint(fullyQualifiedNamespace)
      .apiVersion(SchemaRegistryVersion.getLatest.getVersion)
      .pipeline(httpPipeline)
      .buildClient
  }



}
