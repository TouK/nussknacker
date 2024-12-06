package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.formatter

import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder, Json}
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.api.test.TestRecord
import pl.touk.nussknacker.engine.kafka.consumerrecord.SerializableConsumerRecord
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter, serialization}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  ContentTypes,
  ContentTypesSchemas,
  SchemaId,
  SchemaIdFromMessageExtractor,
  SchemaRegistryClient,
  SchemaWithMetadata,
  StringSchemaId
}

import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

abstract class AbstractSchemaBasedRecordFormatter[K: ClassTag, V: ClassTag] extends RecordFormatter {

  import SchemaBasedSerializableConsumerRecord._
  import pl.touk.nussknacker.engine.api.CirceUtil._

  protected def kafkaConfig: KafkaConfig

  protected def schemaRegistryClient: SchemaRegistryClient

  protected def deserializationSchema: serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]]

  protected def schemaIdFromMessageExtractor: SchemaIdFromMessageExtractor

  /**
    * Step 1: Deserialize raw kafka event to record domain (e.g. GenericRecord).
    * Step 2: Create Encoders that convert record to json
    * Step 3: Encode event's data with schema id's with derived encoder.
    */
  override protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): TestRecord = {
    val keySchemaIdOpt = if (kafkaConfig.useStringForKey) {
      None
    } else {
      schemaIdFromMessageExtractor.getSchemaId(record.headers(), record.key(), isKey = true).map(_.value)
    }
    val valueSchemaIdOpt =
      schemaIdFromMessageExtractor.getSchemaId(record.headers(), record.value(), isKey = false).map(_.value)
    val deserializedRecord = deserialize(record, valueSchemaIdOpt)

    val serializableRecord = SchemaBasedSerializableConsumerRecord(
      keySchemaIdOpt,
      valueSchemaIdOpt,
      SerializableConsumerRecord(deserializedRecord)
    )
    TestRecord(consumerRecordEncoder(keySchemaIdOpt, valueSchemaIdOpt)(serializableRecord))
  }

  protected def deserialize(
      record: ConsumerRecord[Array[Byte], Array[Byte]],
      valueSchemaIdOpt: Option[SchemaId]
  ): ConsumerRecord[K, V] = {
    deserializationSchema.deserialize(record)
  }

  private def consumerRecordEncoder(
      keySchemaIdOpt: Option[SchemaId],
      valueSchemaIdOpt: Option[SchemaId]
  ): Encoder[SchemaBasedSerializableConsumerRecord[K, V]] = {
    implicit val kE: Encoder[K] = createKeyEncoder(keySchemaIdOpt)
    implicit val vE: Encoder[V] = createValueEncoder(valueSchemaIdOpt)
    implicit val srE: Encoder[SerializableConsumerRecord[K, V]] =
      deriveConfiguredEncoder[SerializableConsumerRecord[K, V]]
    deriveConfiguredEncoder
  }

  private def createKeyEncoder(schemaIdOpt: Option[SchemaId]): Encoder[K] = {
    case str: String => Json.fromString(str)
    case key         => formatMessage(schemaIdOpt, key)
  }

  private def createValueEncoder(schemaIdOpt: Option[SchemaId]): Encoder[V] = (value: V) =>
    formatMessage(schemaIdOpt, value)

  protected def formatMessage(schemaIdOpt: Option[SchemaId], data: Any): Json

  /**
    * Step 1: Deserialize raw json bytes to SchemaBasedSerializableConsumerRecord[Json, Json] domain without interpreting key and value content.
    * Step 2: Create key and value json-to-record interpreter based on schema id's provided in json.
    * Step 3: Use interpreter to create raw kafka ConsumerRecord
    */
  override def parseRecord(
      topic: TopicName.ForSource,
      testRecord: TestRecord
  ): ConsumerRecord[Array[Byte], Array[Byte]] = {
    val record = decodeJsonUnsafe(testRecord.json)(consumerRecordDecoder)

    def serializeKeyValue(keyOpt: Option[Json], value: Json): (Array[Byte], Array[Byte]) = {
      val keyBytes = if (kafkaConfig.useStringForKey) {
        keyOpt match {
          // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
          case Some(j) if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
          case None                  => null
          case _                     => throw new IllegalStateException()
        }
      } else {
        val keySchemaOpt = record.keySchemaId.map(schemaRegistryClient.getSchemaById).map(_.schema)
        keyOpt
          .map(keyJson => readRecordKeyMessage(keySchemaOpt, topic, keyJson))
          .getOrElse(throw new IllegalArgumentException("Error reading key schema: expected valid avro key"))
      }

      if (schemaRegistryClient.isTopicWithSchema(
          topic.name,
          kafkaConfig
        )) {
        val valueSchemaOpt = record.valueSchemaId.map(schemaRegistryClient.getSchemaById).map(_.schema)
        val valueBytes     = readValueMessage(valueSchemaOpt, topic, value)
        (keyBytes, valueBytes)
      } else {
        val schema = record.valueSchemaId.flatMap {
          case StringSchemaId(contentType) =>
            if (contentType.equals(ContentTypes.JSON.toString)) {
              Some(
                SchemaWithMetadata(
                  ContentTypesSchemas.schemaForJson,
                  SchemaId.fromString(ContentTypes.JSON.toString)
                ).schema
              )
            } else if (contentType.equals(ContentTypes.PLAIN.toString)) {
              None
            } else
              throw new IllegalStateException("Schemaless topic should have json or plain content type, got neither")
          case _ =>
            throw new IllegalStateException("Schemaless topic should have json or plain content type, got neither")

        }
        val valueBytes = readValueMessage(schema, topic, value)
        (keyBytes, valueBytes)
      }

    }

    record.consumerRecord.copy(topic = Some(topic.name)).toKafkaConsumerRecord(topic, serializeKeyValue)
  }

  protected def readRecordKeyMessage(
      schemaOpt: Option[ParsedSchema],
      topic: TopicName.ForSource,
      jsonObj: Json
  ): Array[Byte]

  protected def readValueMessage(
      schemaOpt: Option[ParsedSchema],
      topic: TopicName.ForSource,
      jsonObj: Json
  ): Array[Byte]

}

case class SchemaBasedSerializableConsumerRecord[K, V](
    keySchemaId: Option[SchemaId],
    valueSchemaId: Option[SchemaId],
    consumerRecord: SerializableConsumerRecord[K, V]
)

object SchemaBasedSerializableConsumerRecord {
  import pl.touk.nussknacker.engine.api.CirceUtil._

  implicit val serializableRecordDecoder: Decoder[SerializableConsumerRecord[Json, Json]] = deriveConfiguredDecoder
  val consumerRecordDecoder: Decoder[SchemaBasedSerializableConsumerRecord[Json, Json]]   = deriveConfiguredDecoder
  implicit val serializableRecordEncoder: Encoder[SerializableConsumerRecord[Json, Json]] = deriveConfiguredEncoder
  implicit val consumerRecordEncoder: Encoder[SchemaBasedSerializableConsumerRecord[Json, Json]] =
    deriveConfiguredEncoder
}
