package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder, Json}
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.kafka.consumerrecord.SerializableConsumerRecord
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter, RecordFormatterFactory, serialization}

import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

/**
  * RecordFormatter factory for kafka avro sources with avro payload.
  *
  * @param schemaRegistryClientFactory
  */
class ConfluentAvroToJsonFormatterFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory) extends RecordFormatterFactory {

  override def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]]): RecordFormatter = {

    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)
    val messageFormatter = new ConfluentAvroMessageFormatter(schemaRegistryClient.client)
    val messageReader = new ConfluentAvroMessageReader(schemaRegistryClient.client)

    new ConfluentAvroToJsonFormatter(kafkaConfig, schemaRegistryClient.client, messageFormatter, messageReader, kafkaSourceDeserializationSchema)
  }

}

/**
  * Formatter uses writer schema ids to assure test data represent raw events data, without schema evolution (which adjusts data to reader schema).
  * Test data record contains data of ConsumerRecord and contains key and value schema ids (see [AvroSerializableConsumerRecord]).
  *
  * @tparam K - key type passed from KafkaAvroSourceFactory, used to determine which datumReaderWriter use (e.g. specific or generic)
  * @tparam V - value type passed from KafkaAvroSourceFactory, used to determine which datumReaderWriter use (e.g. specific or generic)
  */
class ConfluentAvroToJsonFormatter[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig,
                                                             schemaRegistryClient: SchemaRegistryClient,
                                                             messageFormatter: ConfluentAvroMessageFormatter,
                                                             messageReader: ConfluentAvroMessageReader,
                                                             deserializationSchema: serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]]
                                                            ) extends RecordFormatter {

  /**
    * Step 1: Deserialize raw kafka event to GenericRecord/SpecificRecord domain.
    * Step 2: Create Encoders that use ConfluentAvroMessageFormatter to convert avro object to json
    * Step 3: Encode event's data with schema id's with derived encoder.
    */
  override protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte] = {
    val deserializedRecord = deserializationSchema.deserialize(record)
    val serializableRecord = AvroSerializableConsumerRecord(
      if (kafkaConfig.useStringForKey) None else Option(record.key()).map(ConfluentUtils.readId),
      Option(record.value()).map(ConfluentUtils.readId).getOrElse(throw new IllegalArgumentException("Failed to read value schema id")),
      SerializableConsumerRecord(deserializedRecord)
    )

    consumerRecordEncoder(serializableRecord).noSpaces.getBytes(StandardCharsets.UTF_8)
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
        val keySchema = record.keySchemaId.map(id => getSchemaById(id)).getOrElse(throw new IllegalArgumentException("Error reading key schema: empty schema id"))
        keyOpt.map(keyJson => messageReader.readJson(keyJson, keySchema, ConfluentUtils.keySubject(topic))).getOrElse(throw new IllegalArgumentException("Error reading key schema: expected valid avro key"))
      }
      val valueSchema = getSchemaById(record.valueSchemaId)
      val valueBytes = messageReader.readJson(value, valueSchema, ConfluentUtils.valueSubject(topic))
      (keyBytes, valueBytes)
    }

    record.consumerRecord.toKafkaConsumerRecord(topic, serializeKeyValue)
  }

  protected def createKeyEncoder(messageFormatter: ConfluentAvroMessageFormatter): Encoder[K] = {
    new Encoder[K] {
      override def apply(key: K): Json = key match {
        case str: String => Json.fromString(str)
        case _ => messageFormatter.asJson[K](key) // generic or specific record
      }
    }
  }

  protected def createValueEncoder(messageFormatter: ConfluentAvroMessageFormatter): Encoder[V] = {
    new Encoder[V] {
      override def apply(value: V): Json = {
        messageFormatter.asJson[V](value)
      }
    }
  }

  implicit protected val serializableRecordDecoder: Decoder[SerializableConsumerRecord[Json, Json]] = deriveConfiguredDecoder
  protected val consumerRecordDecoder: Decoder[AvroSerializableConsumerRecord[Json, Json]] = deriveConfiguredDecoder

  implicit protected val keyEncoder: Encoder[K] = createKeyEncoder(messageFormatter)
  implicit protected val valueEncoder: Encoder[V] = createValueEncoder(messageFormatter)
  implicit protected val serializableRecordEncoder: Encoder[SerializableConsumerRecord[K, V]] = deriveConfiguredEncoder
  protected val consumerRecordEncoder: Encoder[AvroSerializableConsumerRecord[K, V]] = deriveConfiguredEncoder

  private def getSchemaById(schemaId: Int): Schema = {
    val parsedSchema = schemaRegistryClient.getSchemaById(schemaId)
    ConfluentUtils.extractSchema(parsedSchema)
  }

  override protected def testDataSplit: TestDataSplit = TestParsingUtils.newLineSplit
}

case class AvroSerializableConsumerRecord[K, V](keySchemaId: Option[Int],
                                                valueSchemaId: Int,
                                                consumerRecord: SerializableConsumerRecord[K, V])
