package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.formatter.AbstractSchemaBasedRecordFormatter
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.json.KafkaJsonKeyValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  SchemaId,
  SchemaIdFromMessageExtractor,
  SchemaRegistryClient,
  SchemaRegistryClientFactory
}

import scala.reflect.ClassTag

class UniversalToJsonFormatterFactory(
    schemaRegistryClientFactory: SchemaRegistryClientFactory,
    createSchemaIdFromMessageExtractor: SchemaRegistryClient => SchemaIdFromMessageExtractor
) extends RecordFormatterFactory {

  override def create[K: ClassTag, V: ClassTag](
      kafkaConfig: KafkaConfig,
      kafkaSourceDeserializationSchema: serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]]
  ): RecordFormatter = {
    val schemaRegistryClient         = schemaRegistryClientFactory.create(kafkaConfig)
    val formatterSupportDispatcher   = new RecordFormatterSupportDispatcher(kafkaConfig, schemaRegistryClient)
    val schemaIdFromMessageExtractor = createSchemaIdFromMessageExtractor(schemaRegistryClient)
    new UniversalToJsonFormatter(
      kafkaConfig,
      schemaRegistryClient,
      formatterSupportDispatcher,
      kafkaSourceDeserializationSchema,
      schemaIdFromMessageExtractor
    )
  }

}

class UniversalToJsonFormatter[K: ClassTag, V: ClassTag](
    protected val kafkaConfig: KafkaConfig,
    protected val schemaRegistryClient: Option[SchemaRegistryClient],
    recordFormatterSupportDispatcher: RecordFormatterSupportDispatcher,
    protected val deserializationSchema: serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]],
    protected val schemaIdFromMessageExtractor: SchemaIdFromMessageExtractor
) extends AbstractSchemaBasedRecordFormatter[K, V] {

  private lazy val jsonPayloadToJsonDeserializer =
    new KafkaJsonKeyValueDeserializationSchemaFactory().create[K, V](kafkaConfig, None, None)

  override protected def deserialize(
      record: ConsumerRecord[Array[Byte], Array[Byte]],
      valueSchemaIdOpt: Option[SchemaId]
  ): ConsumerRecord[K, V] = {
    if (valueSchemaIdOpt.isDefined) {
      super.deserialize(record, valueSchemaIdOpt)
    } else {
      jsonPayloadToJsonDeserializer.deserialize(record)
    }
  }

  protected def formatMessage(schemaIdOpt: Option[SchemaId], data: Any): Json = {
    // We do not support formatting AVRO messages without schemaId to json. So when schema is missing we assume it must be JSON payload.
    val support = schemaIdOpt
      .map(schemaRegistryClient.getSchemaById)
      .map(_.schema.schemaType())
      .map(recordFormatterSupportDispatcher.forSchemaType)
      .getOrElse(JsonPayloadRecordFormatterSupport)
    support.formatMessage(data)
  }

  protected def readRecordKeyMessage(
      schemaOpt: Option[ParsedSchema],
      topic: TopicName.ForSource,
      jsonObj: Json
  ): Array[Byte] = {
    // We do not support reading AVRO messages without schemaId. So when schema is missing we assume it must be JSON payload.
    val support = schemaOpt
      .map(_.schemaType())
      .map(recordFormatterSupportDispatcher.forSchemaType)
      .getOrElse(JsonPayloadRecordFormatterSupport)
    support.readKeyMessage(topic, schemaOpt, jsonObj)
  }

  protected def readValueMessage(
      schemaOpt: Option[ParsedSchema],
      topic: TopicName.ForSource,
      jsonObj: Json
  ): Array[Byte] = {
    // We do not support reading AVRO messages without schemaId. So when schema is missing we assume it must be JSON payload.
    val support = schemaOpt
      .map(_.schemaType())
      .map(recordFormatterSupportDispatcher.forSchemaType)
      .getOrElse(JsonPayloadRecordFormatterSupport)
    support.readValueMessage(topic, schemaOpt, jsonObj)
  }

}
