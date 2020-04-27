package pl.touk.nussknacker.engine.avro.fixed

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import org.apache.avro.Schema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka.{KafkaDeserializationSchema, KafkaSerializationSchema}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryClient
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryClientFactory.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.ConfluentAvroToJsonFormatter
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.{ConfluentAvroDeserializationSchemaFactory, ConfluentAvroSerializationSchemaFactory, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroUtils, KafkaAvroSchemaProvider}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter}

/**
  * This class has fake implementation of serializer and deserializer based on Confluent implementations - it's hack
  * to serialization / deserialization schema without SchemaRegistry.
  *
  * @TODO: In future we should create own serializator and deserializator for FixedSchema
  */
class KafkaAvroFixedSchemaProvider[T: TypeInformation](val topic: String,
                                                       val stringSchema: String,
                                                       val kafkaConfig: KafkaConfig,
                                                       val formatKey: Boolean,
                                                       val useSpecificAvroReader: Boolean) extends KafkaAvroSchemaProvider[T] {

  lazy val factory: ConfluentSchemaRegistryClientFactory = new ConfluentSchemaRegistryClientFactory {
    override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient = {
      val schema: Schema = AvroUtils.createSchema(stringSchema)
      new MockSchemaRegistryClient with SchemaRegistryClient {
        override def getBySubjectAndId(subject: String, version: Int): Schema = schema

        override def getLatestSchema(subject: String): Schema = schema

        override def getById(id: Int): Schema = schema
      }
    }
  }

  lazy val deserializationSchemaFactory = new ConfluentAvroDeserializationSchemaFactory(factory, useSpecificAvroReader)

  lazy val serializationSchemaFactory = new ConfluentAvroSerializationSchemaFactory(factory)

  override def deserializationSchema: KafkaDeserializationSchema[T] =
    deserializationSchemaFactory.create(List(topic), kafkaConfig)

  override def serializationSchema: KafkaSerializationSchema[Any] =
    serializationSchemaFactory.create(topic, kafkaConfig)

  override def typeDefinition: typing.TypingResult =
    AvroSchemaTypeDefinitionExtractor.typeDefinition(AvroUtils.createSchema(stringSchema))

  override def recordFormatter: Option[RecordFormatter] =
    Some(ConfluentAvroToJsonFormatter(factory.createSchemaRegistryClient(kafkaConfig), topic, formatKey))

}
