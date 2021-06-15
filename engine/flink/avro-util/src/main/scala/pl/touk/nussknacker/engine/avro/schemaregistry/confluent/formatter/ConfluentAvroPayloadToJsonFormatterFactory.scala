package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import io.circe.Json
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.kafka.consumerrecord.{ConsumerRecordToJsonFormatterFactory, SerializableConsumerRecord, SerializableConsumerRecordSerializer}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter, RecordFormatterFactory}

import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

class ConfluentAvroPayloadToJsonFormatterFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory) extends ConsumerRecordToJsonFormatterFactory[Json, Json] {

  override def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]): RecordFormatter = {
    new AvroConsumerRecordToJsonFormatter(
      createFormatterDeserializer[K, V](kafkaConfig, kafkaSourceDeserializationSchema).asInstanceOf[KafkaDeserializationSchema[ConsumerRecord[Json, Json]]],
      createFormatterSerializer[K, V](kafkaConfig).asInstanceOf[SerializableConsumerRecordSerializer[Json, Json]]
    )
  }

  override protected def createFormatterDeserializer[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]): KafkaDeserializationSchema[ConsumerRecord[K, V]] = {
    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)
    val messageFormatter = new ConfluentAvroMessageFormatter(schemaRegistryClient.client)

    val deserializer = new KafkaDeserializationSchema[ConsumerRecord[Json, Json]] {
      override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]]): ConsumerRecord[Json, Json] = {
        val formattedKey = if (kafkaConfig.useStringForKey) {
          Json.fromString(new String(record.key(), StandardCharsets.UTF_8))
        } else {
          messageFormatter.asJson[K](record.key())
        }
        val formattedValue = messageFormatter.asJson[V](record.value())
        new ConsumerRecord(
          record.topic(),
          record.partition(),
          record.offset(),
          record.timestamp(),
          record.timestampType(),
          ConsumerRecord.NULL_CHECKSUM.toLong,
          record.serializedKeySize(),
          record.serializedValueSize(),
          formattedKey,
          formattedValue,
          record.headers,
          record.leaderEpoch()
        )
      }

      override def isEndOfStream(nextElement: ConsumerRecord[Json, Json]): Boolean = false

      override def getProducedType: TypeInformation[ConsumerRecord[Json, Json]] = TypeInformation.of(classOf[ConsumerRecord[Json, Json]])
    }
    deserializer.asInstanceOf[KafkaDeserializationSchema[ConsumerRecord[K, V]]]
  }

  override protected def createFormatterSerializer[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig): SerializableConsumerRecordSerializer[K, V]  = {
    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)
    val createReader = (topic: String) => new ConfluentAvroMessageReader(schemaRegistryClient.client, topic)

    val serializer = new SerializableConsumerRecordSerializer[Json, Json]{

      override protected def extractKey(topic: String, record: SerializableConsumerRecord[Json, Json]): Array[Byte] = {
        val reader = createReader(topic)
        val keySchemaIdOpt = record.asInstanceOf[AvroSerializableConsumerRecord[Json, Json]].keySchemaId
        val keyBytes = if (kafkaConfig.useStringForKey) {
          record.key match {
            // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
            case Some(j) if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
            case Some(j) => j.noSpaces.getBytes(StandardCharsets.UTF_8)
            case None => Array.emptyByteArray
          }
        } else {
          val keySchema = keySchemaIdOpt.map(id => reader.schemaById(id)).getOrElse(throw new IllegalArgumentException("chuj dupa i kamieni kupa"))
          record.key.map(keyJson => reader.readJson[K](keyJson, Option(keySchema), ConfluentUtils.keySubject(topic))).getOrElse(Array.emptyByteArray)
        }
        keyBytes
      }

      override protected def extractValue(topic: String, record: SerializableConsumerRecord[Json, Json]): Array[Byte] = {
        val reader = createReader(topic)
        val valueSchemaId = record.asInstanceOf[AvroSerializableConsumerRecord[Json, Json]].valueSchemaId
        val valueSchema = reader.schemaById(valueSchemaId)
        val valueBytes = reader.readJson[V](record.value, Option(valueSchema), ConfluentUtils.valueSubject(topic))
        valueBytes
      }
    }
    serializer.asInstanceOf[SerializableConsumerRecordSerializer[K, V]]
  }

}
