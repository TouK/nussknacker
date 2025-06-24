package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.formatter

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import pl.touk.nussknacker.engine.kafka.consumerrecord.SerializableConsumerRecord
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaId

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
