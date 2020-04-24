package pl.touk.nussknacker.engine.avro.typed

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import org.apache.avro.Schema
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryClient
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryClientFactory.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.ConfluentAvroToJsonFormatter
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.{ConfluentAvroDeserializationSchemaFactory, ConfluentAvroSerializationSchemaFactory, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.{AvroUtils, SchemaAvroProvider}
import pl.touk.nussknacker.engine.kafka.serialization.{DeserializationSchemaFactory, SerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter}

class TypedSchemaProvider[T: TypeInformation](val kafkaConfig: KafkaConfig,
                                              val formatKey: Boolean,
                                              val useSpecificAvroReader: Boolean) extends SchemaAvroProvider[T] {

  override def deserializationSchemaFactory(avro: Option[String]): DeserializationSchemaFactory[T] =
    new ConfluentAvroDeserializationSchemaFactory(createFactory(parseSchema(avro)), useSpecificAvroReader)

  override def serializationSchemaFactory(avro: Option[String]): SerializationSchemaFactory[Any] =
    new ConfluentAvroSerializationSchemaFactory(createFactory(parseSchema(avro)))

  override def typeDefinition(topic: String, version: Option[Int], avro: Option[String]): typing.TypingResult =
    AvroSchemaTypeDefinitionExtractor.typeDefinition(parseSchema(avro))

  override def recordFormatter(topic: String, avro: Option[String]): Option[RecordFormatter] =
    Some(ConfluentAvroToJsonFormatter(createFactory(parseSchema(avro)).createSchemaRegistryClient(kafkaConfig), topic, formatKey))

  /**
    * It's fake implementation to always returning Schema from - it's hack to serialization / deserialization schema without SchemaRegistry.
    * In future we should create own serializator and deserializator for TypedSchema
    * @param schema
    * @return
    */
  private def createFactory(schema: Schema) =
    new ConfluentSchemaRegistryClientFactory {
      override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient =
        new MockSchemaRegistryClient with SchemaRegistryClient {
          override def getBySubjectAndId(subject: String, version: Int): Schema = schema
          override def getLatestSchema(subject: String): Schema = schema
          override def getById(id: Int): Schema = schema
        }
    }

  private def parseSchema(schema: Option[String]): Schema =
    schema
      .map(AvroUtils.createSchema)
      .getOrElse(throw new IllegalArgumentException("Missing AvroSchema param."))

}
