package pl.touk.nussknacker.engine.lite.util.test.confluent

import org.apache.avro.generic.GenericContainer
import org.apache.kafka.common.header.Headers
import pl.touk.nussknacker.engine.lite.util.test.KafkaAvroElementSerde
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaId, SchemaIdFromMessageExtractor}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.schemaid.SchemaIdFromPayloadInConfluentFormat

object ConfluentKafkaAvroElementSerde extends KafkaAvroElementSerde {

  override def serializeAvroElement(containerData: GenericContainer, schemaId: SchemaId, headers: Headers, isKey: Boolean): Array[Byte] = {
    ConfluentUtils.serializeContainerToBytesArray(containerData, schemaId)
  }

  override val schemaIdFromMessageExtractor: SchemaIdFromMessageExtractor = SchemaIdFromPayloadInConfluentFormat

}
