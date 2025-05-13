package pl.touk.nussknacker.engine.schemedkafka.helpers

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.schemedkafka.{AvroUtils, RuntimeSchemaData}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaId
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.GenericRecordSchemaIdSerializationSupport
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.AvroPayloadDeserializer

import java.io.IOException
import java.nio.ByteBuffer

class SimpleKafkaAvroDeserializer(schemaRegistry: SchemaRegistryClient) extends Deserializer[Any] {

  protected lazy val decoderFactory: DecoderFactory = DecoderFactory.get()

  private lazy val confluentAvroPayloadDeserializer = new AvroPayloadDeserializer(
    new GenericRecordSchemaIdSerializationSupport(schemaIdSerializationEnabled = true),
    decoderFactory
  )

  override def deserialize(topic: String, payload: Array[Byte]): Any = {
    val buffer = ConfluentUtils.parsePayloadToByteBuffer(payload).valueOr(ex => throw ex)
    read(buffer)
  }

  protected def read(buffer: ByteBuffer): AnyRef = {
    var schemaId = -1

    try {
      schemaId = buffer.getInt
      val parsedSchema     = schemaRegistry.getSchemaById(schemaId)
      val writerSchemaData = RuntimeSchemaData(AvroUtils.extractSchema(parsedSchema), Some(SchemaId.fromInt(schemaId)))
      confluentAvroPayloadDeserializer.deserialize(
        None,
        writerSchemaData.toParsedSchemaData,
        buffer
      )
    } catch {
      case exc: RestClientException =>
        throw new SerializationException(s"Error retrieving Avro schema for id : $schemaId", exc)
      case exc @ (_: RuntimeException | _: IOException) =>
        // avro deserialization may throw IOException, AvroRuntimeException, NullPointerException, etc
        throw new SerializationException(s"Error deserializing Avro message for id: $schemaId", exc)
    }
  }

}
