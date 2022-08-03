package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder, Json}
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.{ConfluentUtils, UniversalSchemaSupport}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.universal.ConfluentUniversalKafkaSerde.{KeySchemaIdHeaderName, _}
import pl.touk.nussknacker.engine.kafka.consumerrecord.SerializableConsumerRecord
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter, RecordFormatterFactory, serialization}

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
    new UniversalToJsonFormatter(kafkaConfig, schemaRegistryClient.client, kafkaSourceDeserializationSchema)
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
                                                         schemaRegistryClient: SchemaRegistryClient,
                                                         deserializationSchema: serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]]
                                                        ) extends RecordFormatter {

  private def formatter(schema: ParsedSchema) = UniversalSchemaSupport.forSchemaType(schema.schemaType()).messageFormatter(schemaRegistryClient)
  private def reader(schema: ParsedSchema) = UniversalSchemaSupport.forSchemaType(schema.schemaType()).messageReader(schema, schemaRegistryClient)

  /**
   * Step 1: Deserialize raw kafka event to GenericRecord/SpecificRecord domain.
   * Step 2: Create Encoders that use ConfluentAvroMessageFormatter to convert avro object to json
   * Step 3: Encode event's data with schema id's with derived encoder.
   */
  override protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte] = {
    val deserializedRecord = deserializationSchema.deserialize(record)

    val keySchemaIdOpt = if (kafkaConfig.useStringForKey) None else {
      record.headers().getSchemaId(KeySchemaIdHeaderName).orElse(Option(record.key()).map(ConfluentUtils.readId))
    }

    val valueSchemaId = record.headers().getSchemaId(ValueSchemaIdHeaderName)
      .orElse(Option(record.value()).map(ConfluentUtils.readId))
      .getOrElse(throw new IllegalArgumentException("Failed to read value schema id"))

    val serializableRecord = UniversalSerializableConsumerRecord(
      keySchemaIdOpt,
      valueSchemaId,
      SerializableConsumerRecord(deserializedRecord)
    )
    consumerRecordEncoder(keySchemaIdOpt.map(getParsedSchemaById), getParsedSchemaById(valueSchemaId))(serializableRecord).noSpaces.getBytes(StandardCharsets.UTF_8)
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
        val keySchema = record.keySchemaId.map(id => getParsedSchemaById(id)).getOrElse(throw new IllegalArgumentException("Error reading key schema: empty schema id"))
        keyOpt.map(keyJson => reader(keySchema)(keyJson, ConfluentUtils.keySubject(topic))
          ).getOrElse(throw new IllegalArgumentException("Error reading key schema: expected valid key"))
      }
      val valueSchema = getParsedSchemaById(record.valueSchemaId)
      val valueBytes = reader(valueSchema)(value, ConfluentUtils.valueSubject(topic))
      (keyBytes, valueBytes)
    }

    record.consumerRecord.toKafkaConsumerRecord(topic, serializeKeyValue)
  }


  protected def createKeyEncoder(schemaOpt: Option[ParsedSchema]): Encoder[K] = {
    case str: String => Json.fromString(str)
    case key => formatter(schemaOpt.get)(key)
  }

  protected def createValueEncoder(schema: ParsedSchema): Encoder[V] = (value: V) => formatter(schema)(value)

  implicit protected val serializableRecordDecoder: Decoder[SerializableConsumerRecord[Json, Json]] = deriveConfiguredDecoder
  protected val consumerRecordDecoder: Decoder[UniversalSerializableConsumerRecord[Json, Json]] = deriveConfiguredDecoder

  protected def consumerRecordEncoder(keySchemaOpt: Option[ParsedSchema], valueSchema: ParsedSchema): Encoder[UniversalSerializableConsumerRecord[K, V]] = {
    implicit val kE: Encoder[K] = createKeyEncoder(keySchemaOpt)
    implicit val vE: Encoder[V] = createValueEncoder(valueSchema)
    implicit val srE: Encoder[SerializableConsumerRecord[K, V]] = deriveConfiguredEncoder
    deriveConfiguredEncoder
  }

  private def getParsedSchemaById(schemaId:Int): ParsedSchema = schemaRegistryClient.getSchemaById(schemaId)

  override protected def testDataSplit: TestDataSplit = TestParsingUtils.newLineSplit
}

case class UniversalSerializableConsumerRecord[K, V](keySchemaId: Option[Int],
                                                     valueSchemaId: Int,
                                                     consumerRecord: SerializableConsumerRecord[K, V])