package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.ConfluentAvroToJsonFormatter.{createKeyEncoder, createValueEncoder}
import pl.touk.nussknacker.engine.kafka.consumerrecord.SerializableConsumerRecord
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter, RecordFormatterFactory}

import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

class ConfluentAvroToJsonFormatterFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory) extends RecordFormatterFactory {

  override def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]): RecordFormatter = {

    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)
    val messageFormatter = new ConfluentAvroMessageFormatter(schemaRegistryClient.client)
    val createReader = (topic: String) => new ConfluentAvroMessageReader(schemaRegistryClient.client, topic)

    new ConfluentAvroToJsonFormatter(kafkaConfig, messageFormatter, createReader, kafkaSourceDeserializationSchema)
  }

}

/**
  * @tparam K - key type passed from KafkaAvroSourceFactory, used to determine which datumReaderWriter use (e.g. specific or generic)
  * @tparam V - value type passed from KafkaAvroSourceFactory, used to determine which datumReaderWriter use (e.g. specific or generic)
  */
class ConfluentAvroToJsonFormatter[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig,
                                                             messageFormatter: ConfluentAvroMessageFormatter,
                                                             createReader: String => ConfluentAvroMessageReader,
                                                             deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]
                                                            ) extends RecordFormatter {

  /**
    * Step 1: Deserialize raw kafka event to GenericRecord/SpecificRecord domain.
    * Step 2: Create Encoders that use ConfluentAvroMessageFormatter to convert avro object to json
    * Step 3: Encode event's data with schema id's with derived encoder.
    */
  override protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte] = {
    val deserializedRecord = deserializationSchema.deserialize(record)
    val serializableRecord = AvroSerializableConsumerRecord(
      messageFormatter.getSchemaIdOpt(record.key()),
      messageFormatter.getSchemaIdOpt(record.value()).get,
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
    val record = CirceUtil.decodeJsonUnsafe(bytes)(consumerRecordDecoder)

    def serializeKeyValue(keyOpt: Option[Json], value: Json): (Array[Byte], Array[Byte]) = {
      val reader = createReader(topic)
      val keyBytes = if (kafkaConfig.useStringForKey) {
        keyOpt match {
          // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
          case Some(j) if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
          case Some(j) => j.noSpaces.getBytes(StandardCharsets.UTF_8)
          case None => null
        }
      } else {
        val keySchema = record.keySchemaId.map(id => reader.schemaById(id)).getOrElse(throw new IllegalArgumentException("Error reading schema: empty schema id"))
        keyOpt.map(keyJson => reader.readJson[K](keyJson, Option(keySchema), ConfluentUtils.keySubject(topic))).getOrElse(Array.emptyByteArray)
      }
      val valueSchema = reader.schemaById(record.valueSchemaId)
      val valueBytes = reader.readJson[V](value, Option(valueSchema), ConfluentUtils.valueSubject(topic))
      (keyBytes, valueBytes)
    }
    record.consumerRecord.toKafkaConsumerRecord(topic, serializeKeyValue)
  }

  override protected def testDataSplit: TestDataSplit = TestParsingUtils.newLineSplit

  implicit protected val serializableRecordDecoder: Decoder[SerializableConsumerRecord[Json, Json]] = deriveDecoder
  protected val consumerRecordDecoder: Decoder[AvroSerializableConsumerRecord[Json, Json]] = deriveDecoder

  implicit protected val keyEncoder: Encoder[K] = createKeyEncoder[K](kafkaConfig.useStringForKey, messageFormatter)
  implicit protected val valueEncoder: Encoder[V] = createValueEncoder[V](messageFormatter)
  implicit protected val serializableRecordEncoder: Encoder[SerializableConsumerRecord[K, V]] = deriveEncoder
  protected val consumerRecordEncoder: Encoder[AvroSerializableConsumerRecord[K, V]] = deriveEncoder

}

object ConfluentAvroToJsonFormatter {

  def createKeyEncoder[K: ClassTag](useStringForKey: Boolean, messageFormatter: ConfluentAvroMessageFormatter): Encoder[K] = {
    new Encoder[K] {
      override def apply(key: K): Json = if (useStringForKey) {
        Json.fromString(key.asInstanceOf[String])
      } else {
        messageFormatter.asJson[K](key)
      }
    }
  }

  def createValueEncoder[V: ClassTag](messageFormatter: ConfluentAvroMessageFormatter): Encoder[V] = {
    new Encoder[V]{
      override def apply(value: V): Json = {
        messageFormatter.asJson[V](value)
      }
    }
  }

}

case class AvroSerializableConsumerRecord[K, V](keySchemaId: Option[Int],
                                                valueSchemaId: Int,
                                                consumerRecord: SerializableConsumerRecord[K, V])
