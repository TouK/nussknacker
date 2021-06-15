package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import io.circe.Json
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.kafka.consumerrecord.{ConsumerRecordToJsonFormatter, ConsumerRecordToJsonFormatterFactory, SerializableConsumerRecord, SerializableConsumerRecordSerializer}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter, RecordFormatterFactory}

import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

class ConfluentJsonPayloadToJsonFormatterFactory extends ConsumerRecordToJsonFormatterFactory[Json, Json] {

  override def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]): RecordFormatter = {
    new ConsumerRecordToJsonFormatter[Json, Json](
      createFormatterDeserializer[K, V](kafkaConfig, kafkaSourceDeserializationSchema).asInstanceOf[KafkaDeserializationSchema[ConsumerRecord[Json, Json]]],
      createFormatterSerializer[K, V](kafkaConfig).asInstanceOf[SerializableConsumerRecordSerializer[Json, Json]]
    )
  }

  override def createFormatterDeserializer[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]): KafkaDeserializationSchema[ConsumerRecord[K, V]] = {
    val cs = StandardCharsets.UTF_8
    val deserializer = new KafkaDeserializationSchema[ConsumerRecord[Json, Json]] {
      override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]]): ConsumerRecord[Json, Json] = {
        val formattedKey = if (kafkaConfig.useStringForKey) {
          Json.fromString(new String(record.key(), cs))
        } else {
          io.circe.parser.parse(new String(record.key(), cs)).right.get
        }
        val formattedValue = io.circe.parser.parse(new String(record.value(), cs)).right.get
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
    val serializer = new SerializableConsumerRecordSerializer[Json, Json]{
      override protected def extractKey(topic: String, record: SerializableConsumerRecord[Json, Json]): Array[Byte] = {
        record.key match {
          // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
          case Some(j) if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
          case Some(j) => j.noSpaces.getBytes(StandardCharsets.UTF_8)
          case None => Array.emptyByteArray
        }
      }
      override protected def extractValue(topic: String, record: SerializableConsumerRecord[Json, Json]): Array[Byte] = {
        val cs = StandardCharsets.UTF_8
        record.value.noSpaces.getBytes(cs)
      }
    }
    serializer.asInstanceOf[SerializableConsumerRecordSerializer[K, V]]
  }

}
