package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import cats.data.NonEmptyList
import io.circe.{Encoder, Json}
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord}
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.consumerrecord.SerializableConsumerRecord
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.formatter.SchemaBasedSerializableConsumerRecord
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.json.KafkaJsonKeyValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.util.ListUtil

import java.nio.charset.StandardCharsets

class UniversalToJsonFormatterFactory(
    schemaRegistryClientFactory: SchemaRegistryClientFactory,
    createSchemaIdFromMessageExtractor: SchemaRegistryClient => SchemaIdFromMessageExtractor
) extends Serializable {

  def create[K, V](
      kafkaConfig: KafkaConfig,
      kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]
  ): UniversalToJsonFormatter[K, V] = {
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

/**
 * It is a class for bi-directional conversion between Kafka record and [[TestRecord]]. It is used when data
 * stored on topic aren't in human readable format and you need to add extra step in generation of test data
 * and in reading of these data.
 */
class UniversalToJsonFormatter[K, V](
    protected val kafkaConfig: KafkaConfig,
    protected val schemaRegistryClient: SchemaRegistryClient,
    recordFormatterSupportDispatcher: RecordFormatterSupportDispatcher,
    protected val deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
    protected val schemaIdFromMessageExtractor: SchemaIdFromMessageExtractor
) extends Serializable {

  private lazy val jsonPayloadToJsonDeserializer =
    new KafkaJsonKeyValueDeserializationSchemaFactory().create[K, V](kafkaConfig, None, None)

  import pl.touk.nussknacker.engine.api.CirceUtil._

  import SchemaBasedSerializableConsumerRecord._

  def prepareGeneratedTestData(records: List[ConsumerRecord[Array[Byte], Array[Byte]]]): TestData = {
    val testRecords = records.map { consumerRecord =>
      val testRecord = formatRecord(consumerRecord)
      fillEmptyTimestampFromConsumerRecord(testRecord, consumerRecord)
    }
    TestData(testRecords)
  }

  def generateTestData(topics: NonEmptyList[TopicName.ForSource], size: Int, kafkaConfig: KafkaConfig): TestData = {
    val listsFromAllTopics = topics.map(KafkaUtils.readLastMessages(_, size, kafkaConfig))
    val merged             = ListUtil.mergeLists(listsFromAllTopics.toList, size)
    prepareGeneratedTestData(merged)
  }

  private def fillEmptyTimestampFromConsumerRecord(
      testRecord: TestRecord,
      consumerRecord: ConsumerRecord[_, _]
  ): TestRecord = {
    testRecord.timestamp match {
      case Some(_) => testRecord
      case None    => testRecord.copy(timestamp = getConsumerRecordTimestamp(consumerRecord))
    }
  }

  private def getConsumerRecordTimestamp(consumerRecord: ConsumerRecord[_, _]): Option[Long] = {
    Option(consumerRecord.timestamp()).filterNot(_ == ConsumerRecord.NO_TIMESTAMP)
  }

  /**
   * Step 1: Deserialize raw kafka event to record domain (e.g. GenericRecord).
   * Step 2: Create Encoders that convert record to json
   * Step 3: Encode event's data with schema id's with derived encoder.
   */
  private def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): TestRecord = {
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

  /**
   * Step 1: Deserialize raw json bytes to SchemaBasedSerializableConsumerRecord[Json, Json] domain without interpreting key and value content.
   * Step 2: Create key and value json-to-record interpreter based on schema id's provided in json.
   * Step 3: Use interpreter to create raw kafka ConsumerRecord
   */
  def parseRecord(
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

  private def deserialize(
      record: ConsumerRecord[Array[Byte], Array[Byte]],
      valueSchemaIdOpt: Option[SchemaId]
  ): ConsumerRecord[K, V] = {
    if (valueSchemaIdOpt.isDefined) {
      deserializationSchema.deserialize(record)
    } else {
      jsonPayloadToJsonDeserializer.deserialize(record)
    }
  }

  private def formatMessage(schemaIdOpt: Option[SchemaId], data: Any): Json = {
    // We do not support formatting AVRO messages without schemaId to json. So when schema is missing we assume it must be JSON payload.
    val support = schemaIdOpt
      .map(schemaRegistryClient.getSchemaById)
      .map(_.schema.schemaType())
      .map(recordFormatterSupportDispatcher.forSchemaType)
      .getOrElse(JsonPayloadRecordFormatterSupport)
    support.formatMessage(data)
  }

  private def readRecordKeyMessage(
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

  private def readValueMessage(
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
