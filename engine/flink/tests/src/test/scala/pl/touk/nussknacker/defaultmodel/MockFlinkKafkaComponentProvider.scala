package pl.touk.nussknacker.defaultmodel

import pl.touk.nussknacker.engine.flink.util.transformer.FlinkKafkaComponentProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory

class MockFlinkKafkaComponentProvider(getSchemaRegistryClientFactory: () => SchemaRegistryClientFactory)
    extends FlinkKafkaComponentProvider {

  override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory = getSchemaRegistryClientFactory()

}
