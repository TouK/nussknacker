package pl.touk.nussknacker.engine.lite.util.test.confluent

import org.apache.avro.generic.GenericContainer
import org.apache.kafka.common.header.Headers
import pl.touk.nussknacker.engine.lite.util.test.KafkaAvroElementSerde
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaId, SchemaIdFromMessageExtractor}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.AzureUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.schemaid.SchemaIdFromAzureHeader

object AzureKafkaAvroElementSerde extends KafkaAvroElementSerde {

  override def serializeAvroElement(
      containerData: GenericContainer,
      schemaId: SchemaId,
      headers: Headers,
      isKey: Boolean
  ): Array[Byte] = {
    if (!isKey) {
      headers.add(AzureUtils.avroContentTypeHeader(schemaId))
    }
    AvroUtils.serializeContainerToBytesArray(containerData)
  }

  override def schemaIdFromMessageExtractor: SchemaIdFromMessageExtractor = SchemaIdFromAzureHeader

}
