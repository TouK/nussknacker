package pl.touk.nussknacker.defaultmodel

import pl.touk.nussknacker.defaultmodel.MockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkKafkaComponentProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory

class MockFlinkKafkaComponentProvider extends FlinkKafkaComponentProvider {

  override def providerName: String = "mockKafkaFlink"

  override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory = MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)

}
