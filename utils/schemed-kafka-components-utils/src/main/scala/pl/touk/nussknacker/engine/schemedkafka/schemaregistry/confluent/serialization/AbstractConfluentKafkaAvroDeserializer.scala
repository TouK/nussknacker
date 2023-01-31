package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.serializers.{AbstractKafkaAvroDeserializer, AbstractKafkaSchemaSerDe}
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaId
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.GenericRecordSchemaIdSerializationSupport
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.AvroPayloadDeserializer
import pl.touk.nussknacker.engine.schemedkafka.{AvroUtils, RuntimeSchemaData}

import java.io.IOException
import java.nio.ByteBuffer

/**
 * This class basically do the same as AbstractKafkaAvroDeserializer but use our createDatumReader implementation with time conversions
 */
abstract class AbstractConfluentKafkaAvroDeserializer extends AbstractKafkaAvroDeserializer {

  protected def genericRecordSchemaIdSerializationSupport: GenericRecordSchemaIdSerializationSupport

  protected lazy val decoderFactory: DecoderFactory = DecoderFactory.get()
  private lazy val confluentAvroPayloadDeserializer = new AvroPayloadDeserializer(
    useSchemaReflection, useSpecificAvroReader, genericRecordSchemaIdSerializationSupport, decoderFactory)

  protected def deserialize(topic: String, isKey: java.lang.Boolean, payload: Array[Byte], readerSchema: Option[RuntimeSchemaData[AvroSchema]]): AnyRef = {
    val buffer = ConfluentUtils.parsePayloadToByteBuffer(payload).valueOr(ex => throw ex)
    read(buffer, readerSchema)
  }

  protected def read(buffer: ByteBuffer, expectedSchemaData: Option[RuntimeSchemaData[AvroSchema]]): AnyRef = {
    var schemaId = -1

    try {
      schemaId = buffer.getInt
      val parsedSchema = schemaRegistry.getSchemaById(schemaId)
      val writerSchemaData = RuntimeSchemaData(AvroUtils.extractSchema(parsedSchema), Some(SchemaId.fromInt(schemaId)))
      confluentAvroPayloadDeserializer.deserialize(expectedSchemaData.map(_.toParsedSchemaData), writerSchemaData.toParsedSchemaData, buffer)
    } catch {
      case exc: RestClientException =>
        throw new SerializationException(s"Error retrieving Avro schema for id : $schemaId", exc)
      case exc@(_: RuntimeException | _: IOException) =>
        // avro deserialization may throw IOException, AvroRuntimeException, NullPointerException, etc
        throw new SerializationException(s"Error deserializing Avro message for id: $schemaId", exc)
    }
  }
}
