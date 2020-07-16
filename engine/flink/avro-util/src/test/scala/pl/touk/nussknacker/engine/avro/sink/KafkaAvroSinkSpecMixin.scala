package pl.touk.nussknacker.engine.avro.sink

import org.apache.avro.Schema
import org.apache.avro.generic.GenericContainer
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.avro.TestSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, FullNameV2, PaymentV1}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientBuilder}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}

trait KafkaAvroSinkSpecMixin {

  final protected val avroEncoder = BestEffortAvroEncoder()

  protected def createLazyParam(schema: Schema, data: Map[String, Any]): LazyParameter[GenericContainer] = {
    val record = avroEncoder.encodeRecordOrError(data, schema)
    new LazyParameter[GenericContainer] {
      override def returnType: typing.TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(record.getSchema)
    }
  }

  object KafkaAvroSinkMockSchemaRegistry {

    val fullnameTopic: String = "fullname"

    val schemaRegistryMockClient: CSchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
      .register(fullnameTopic, FullNameV1.schema, 1, isKey = false)
      .register(fullnameTopic, FullNameV2.schema, 2, isKey = false)
      .register(fullnameTopic, PaymentV1.schema, 3, isKey = false)
      .build

    val factory: CachedConfluentSchemaRegistryClientFactory = TestSchemaRegistryClientFactory(schemaRegistryMockClient)
  }
}
