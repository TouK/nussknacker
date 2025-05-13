package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.formatter

import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.{serialization, KafkaConfig, RecordFormatter, RecordFormatterFactory}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.SchemaRegistryBasedSerializerFactory

import scala.reflect.ClassTag

/**
  * RecordFormatter factory for kafka avro sources with avro payload.
  */
class AvroToJsonFormatterFactory(
    schemaRegistryClientFactory: SchemaRegistryClientFactory,
    schemaIdFromMessageExtractor: SchemaIdFromMessageExtractor,
    serializerFactory: SchemaRegistryBasedSerializerFactory
) extends RecordFormatterFactory {

  override def create[K: ClassTag, V: ClassTag](
      kafkaConfig: KafkaConfig,
      kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]
  ): RecordFormatter = {
    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)

    // We pass None to schema, because message readers should not do schema evolution.
    // It is done this way because we want to keep messages in the original format as they were serialized on Kafka
    def createSerializer(isKey: Boolean) =
      serializerFactory.createSerializer(schemaRegistryClient, kafkaConfig, None, isKey)
    val keyMessageReader   = new AvroMessageReader(createSerializer(true))
    val valueMessageReader = new AvroMessageReader(createSerializer(false))

    new AvroToJsonFormatter(
      kafkaConfig,
      schemaRegistryClient,
      keyMessageReader,
      valueMessageReader,
      kafkaSourceDeserializationSchema,
      schemaIdFromMessageExtractor
    )
  }

}

/**
  * Formatter uses writer schema ids to assure test data represent raw events data, without schema evolution (which adjusts data to reader schema).
  * Test data record contains data of ConsumerRecord and contains key and value schema ids (see [SchemaBasedSerializableConsumerRecord]).
  */
class AvroToJsonFormatter[K: ClassTag, V: ClassTag](
    protected val kafkaConfig: KafkaConfig,
    protected val schemaRegistryClient: SchemaRegistryClient,
    keyMessageReader: AvroMessageReader,
    valueMessageReader: AvroMessageReader,
    protected val deserializationSchema: serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]],
    protected val schemaIdFromMessageExtractor: SchemaIdFromMessageExtractor
) extends AbstractSchemaBasedRecordFormatter[K, V] {

  override protected def formatMessage(schemaIdOpt: Option[SchemaId], data: Any): Json =
    AvroMessageFormatter.asJson(data)

  override protected def readRecordKeyMessage(
      schemaOpt: Option[ParsedSchema],
      topic: TopicName.ForSource,
      jsonObj: Json
  ): Array[Byte] = {
    val avroSchema = AvroUtils.extractSchema(
      schemaOpt.getOrElse(throw new IllegalArgumentException("Error reading key schema: empty schema id"))
    )
    keyMessageReader.readJson(jsonObj, avroSchema, topic)
  }

  override protected def readValueMessage(
      schemaOpt: Option[ParsedSchema],
      topic: TopicName.ForSource,
      jsonObj: Json
  ): Array[Byte] = {
    val avroSchema = AvroUtils.extractSchema(
      schemaOpt.getOrElse(throw new IllegalArgumentException("Error reading value schema: empty schema id"))
    )
    valueMessageReader.readJson(jsonObj, avroSchema, topic)
  }

}
