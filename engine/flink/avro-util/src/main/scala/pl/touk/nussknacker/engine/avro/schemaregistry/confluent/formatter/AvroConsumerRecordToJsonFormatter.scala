package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.kafka.ConsumerRecordUtils
import pl.touk.nussknacker.engine.kafka.consumerrecord.{ConsumerRecordToJsonFormatter, SerializableConsumerRecordSerializer, SerializableConsumerRecord}

class AvroConsumerRecordToJsonFormatter(deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[Json, Json]],
                                        keyValueSerializer: SerializableConsumerRecordSerializer[Json, Json])
  extends ConsumerRecordToJsonFormatter(deserializationSchema, keyValueSerializer) {

  override protected val consumerRecordDecoder: Decoder[SerializableConsumerRecord[Json, Json]] =
    deriveDecoder[AvroSerializableConsumerRecord[Json, Json]].asInstanceOf[Decoder[SerializableConsumerRecord[Json, Json]]]
  override protected val consumerRecordEncoder: Encoder[SerializableConsumerRecord[Json, Json]] =
    deriveEncoder[AvroSerializableConsumerRecord[Json, Json]].asInstanceOf[Encoder[SerializableConsumerRecord[Json, Json]]]

  override protected def prepareSerializableRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): SerializableConsumerRecord[Json, Json] = {
    val deserializedRecord = deserializationSchema.deserialize(record)
    AvroSerializableConsumerRecord(deserializedRecord, getWriterSchemaIdOpt(record.key), getWriterSchemaIdOpt(record.value).get)
  }

  private def getWriterSchemaIdOpt(bytes: Array[Byte]): Option[Int] = {
    bytes match {
      case avroContent if avroContent.nonEmpty => Some(ConfluentUtils.readId(avroContent))
      case _ => None
    }
  }
}

case class AvroSerializableConsumerRecord[K, V](key: Option[K],
                                                value: V,
                                                topic: Option[String],
                                                partition: Option[Int],
                                                offset: Option[Long],
                                                timestamp: Option[Long],
                                                timestampType: Option[String],
                                                headers: Option[Map[String, Option[String]]],
                                                leaderEpoch: Option[Int],
                                                keySchemaId: Option[Int],
                                                valueSchemaId: Int) extends SerializableConsumerRecord[K, V]

object AvroSerializableConsumerRecord {
  def apply[K, V](deserializedRecord: ConsumerRecord[K, V], keySchemaIdOpt: Option[Int], valueSchemaId: Int): AvroSerializableConsumerRecord[K, V] = {
    new AvroSerializableConsumerRecord(
      Option(deserializedRecord.key()),
      deserializedRecord.value(),
      Option(deserializedRecord.topic()),
      Option(deserializedRecord.partition()),
      Option(deserializedRecord.offset()),
      Option(deserializedRecord.timestamp()),
      Option(deserializedRecord.timestampType().name),
      Option(ConsumerRecordUtils.toMap(deserializedRecord.headers()).mapValues(s => Option(s))),
      Option(deserializedRecord.leaderEpoch().orElse(null)).map(_.intValue()), //avoids covert null -> 0 conversion
      keySchemaIdOpt,
      valueSchemaId
    )
  }
}