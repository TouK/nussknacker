package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.formatter

import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder, Json}
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.consumerrecord.SerializableConsumerRecord
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.{ConfluentUtils, PayloadType, UniversalComponentsSupport}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.universal.UniversalSchemaIdFromMessageExtractor

import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

/**
 * RecordFormatter factory for kafka avro sources with avro payload.
 *
 * @param schemaRegistryClientFactory
 */
class UniversalToJsonFormatterFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory) extends RecordFormatterFactory {

  override def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]]): RecordFormatter = {
    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)
    new UniversalToJsonFormatter(kafkaConfig, schemaRegistryClient, kafkaSourceDeserializationSchema)
  }
}

/**
 * Formatter uses writer schema ids to assure test data represent raw events data, without schema evolution (which adjusts data to reader schema).
 * Test data record contains data of ConsumerRecord and contains key and value schema ids (see [AvroSerializableConsumerRecord]).
 *
 * @tparam K - key type passed from KafkaAvroSourceFactory, used to determine which datumReaderWriter use (e.g. specific or generic)
 * @tparam V - value type passed from KafkaAvroSourceFactory, used to determine which datumReaderWriter use (e.g. specific or generic)
 */
class UniversalToJsonFormatter[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig,
                                                         override val schemaRegistryClient: ConfluentSchemaRegistryClient,
                                                         deserializationSchema: serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]]
                                                        ) extends RecordFormatter with UniversalSchemaIdFromMessageExtractor {

  private def formatter(schemaOpt: Option[ParsedSchema]) = {
    // We do not support formatting AVRO messages without schemaId to json. So when schema is missing we assume it must be JSON payload.
    val payloadType = schemaOpt.map(s => UniversalComponentsSupport.forSchemaType(s.schemaType())).map(_.payloadType).getOrElse(PayloadType.Json)
    UniversalComponentsSupport.forPayloadType(payloadType).messageFormatter(schemaRegistryClient.client)
  }

  private def reader(schemaOpt: Option[ParsedSchema]) = {
    // We do not support reading AVRO messages without schemaId. So when schema is missing we assume it must be JSON payload.
    val payloadType = schemaOpt.map(s => UniversalComponentsSupport.forSchemaType(s.schemaType())).map(_.payloadType).getOrElse(PayloadType.Json)
    UniversalComponentsSupport.forPayloadType(payloadType).messageReader(schemaOpt, schemaRegistryClient.client)
  }

  /**
   * Step 1: Deserialize raw kafka event to GenericRecord/SpecificRecord domain.
   * Step 2: Create Encoders that use ConfluentAvroMessageFormatter to convert avro object to json
   * Step 3: Encode event's data with schema id's with derived encoder.
   */
  override protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte] = {
    val deserializedRecord = deserializationSchema.deserialize(record)

    val keySchemaIdOpt = if (kafkaConfig.useStringForKey) None else {
      getSchemaIdWhenPresent(record.headers(), record.key(), isKey = true).map(_.value)
    }

    val valueSchemaId = getSchemaIdWhenPresent(record.headers(), record.value(), isKey = false).map(_.value)

    val serializableRecord = UniversalSerializableConsumerRecord(
      keySchemaIdOpt,
      valueSchemaId,
      SerializableConsumerRecord(deserializedRecord)
    )
    consumerRecordEncoder(keySchemaIdOpt.map(getParsedSchemaById), valueSchemaId.map(getParsedSchemaById))(serializableRecord).noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  /**
   * Step 1: Deserialize raw json bytes to AvroSerializableConsumerRecord[Json, Json] domain without interpreting key and value content.
   * Step 2: Create key and value json-to-avro interpreter based on schema id's provided in json.
   * Step 3: Use interpreter to create raw kafka ConsumerRecord
   */
  override protected def parseRecord(topic: String, bytes: Array[Byte]): ConsumerRecord[Array[Byte], Array[Byte]] = {
    val record = decodeJsonUnsafe(bytes)(consumerRecordDecoder)

    def serializeKeyValue(keyOpt: Option[Json], value: Json): (Array[Byte], Array[Byte]) = {
      val keyBytes = if (kafkaConfig.useStringForKey) {
        keyOpt match {
          // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
          case Some(j) if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
          case None => null
        }
      } else {
        val keySchema = record.keySchemaId.map(id => getParsedSchemaById(id))
        keyOpt.map(keyJson => reader(keySchema)(keyJson, ConfluentUtils.keySubject(topic))
          ).getOrElse(throw new IllegalArgumentException("Error reading key schema: expected valid key"))
      }
      val valueSchema = record.valueSchemaId.map(getParsedSchemaById)
      val valueBytes = reader(valueSchema)(value, ConfluentUtils.valueSubject(topic))
      (keyBytes, valueBytes)
    }

    record.consumerRecord.toKafkaConsumerRecord(topic, serializeKeyValue)
  }


  protected def createKeyEncoder(schemaOpt: Option[ParsedSchema]): Encoder[K] = {
    case str: String => Json.fromString(str)
    case key => formatter(schemaOpt)(key)
  }

  protected def createValueEncoder(schemaOpt: Option[ParsedSchema]): Encoder[V] = (value: V) => formatter(schemaOpt)(value)

  implicit protected val serializableRecordDecoder: Decoder[SerializableConsumerRecord[Json, Json]] = deriveConfiguredDecoder
  protected val consumerRecordDecoder: Decoder[UniversalSerializableConsumerRecord[Json, Json]] = deriveConfiguredDecoder

  protected def consumerRecordEncoder(keySchemaOpt: Option[ParsedSchema], valueSchemaOpt: Option[ParsedSchema]): Encoder[UniversalSerializableConsumerRecord[K, V]] = {
    implicit val kE: Encoder[K] = createKeyEncoder(keySchemaOpt)
    implicit val vE: Encoder[V] = createValueEncoder(valueSchemaOpt)
    implicit val srE: Encoder[SerializableConsumerRecord[K, V]] = deriveConfiguredEncoder
    deriveConfiguredEncoder
  }

  private def getParsedSchemaById(schemaId:Int): ParsedSchema = schemaRegistryClient.getSchemaById(schemaId).schema

  override protected def testDataSplit: TestDataSplit = TestParsingUtils.newLineSplit
}

case class UniversalSerializableConsumerRecord[K, V](keySchemaId: Option[Int],
                                                     valueSchemaId: Option[Int],
                                                     consumerRecord: SerializableConsumerRecord[K, V])