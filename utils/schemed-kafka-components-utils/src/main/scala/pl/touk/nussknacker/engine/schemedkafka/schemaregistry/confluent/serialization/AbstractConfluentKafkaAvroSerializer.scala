package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization

import io.confluent.kafka.schemaregistry.avro.{AvroSchema, AvroSchemaUtils}
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.serializers.{AbstractKafkaAvroSerializer, AbstractKafkaSchemaSerDe, NonRecordContainer}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericContainer
import org.apache.avro.io.{Encoder, EncoderFactory}
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.header.Headers
import pl.touk.nussknacker.engine.schemedkafka.schema.{AvroSchemaEvolution, DatumReaderWriterMixin}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaId

import java.io.{ByteArrayOutputStream, IOException, OutputStream}
import java.nio.ByteBuffer
import scala.util.Using

/**
  * Abstract confluent serializer class. Serialize algorithm is copy past from AbstractKafkaAvroSerializer.serializeImpl.
  * Serializer try convert data (in most cases it will be GenericContainer) to indicated schema.
  *
  * There is some problem when GenericContainer has different schema then final schema - DatumWriter throws exception,
  * because data could not support field from schema. When this situation has place wy try to convert data to provided schema by
  * using AvroSchemaEvolution.alignRecordToSchema implementation.
  */
class AbstractConfluentKafkaAvroSerializer(avroSchemaEvolution: AvroSchemaEvolution)
    extends AbstractKafkaAvroSerializer
    with DatumReaderWriterMixin {

  protected val encoderFactory: EncoderFactory = EncoderFactory.get

  def serialize(
      avroSchemaOpt: Option[AvroSchema],
      topic: String,
      data: Any,
      isKey: Boolean,
      headers: Headers
  ): Array[Byte] = {
    if (data == null) {
      null
    } else {

      val (avroSchema, record) = (avroSchemaOpt, data) match {
        case (Some(schema), record: GenericContainer) =>
          (schema, avroSchemaEvolution.alignRecordToSchema(record, schema.rawSchema()))
        case (Some(schema), other) => (schema, other)
        case (None, other) =>
          (new AvroSchema(AvroSchemaUtils.getSchema(data, this.useSchemaReflection, false, false)), other)
      }
      val extracted = record match {
        case nonRecord: NonRecordContainer => nonRecord.getValue
        case other                         => other
      }

      try {
        val schemaId: SchemaId = autoRegisterSchemaIfNeeded(topic, data, isKey, avroSchema)
        writeData(extracted, avroSchema.rawSchema(), schemaId, headers)
      } catch {
        case exc @ (_: RuntimeException | _: IOException) =>
          throw new SerializationException("Error serializing Avro message", exc)
      }
    }
  }

  protected def autoRegisterSchemaIfNeeded(
      topic: String,
      data: Any,
      isKey: Boolean,
      avroSchema: AvroSchema
  ): SchemaId = {
    try {
      val subject = getSubjectName(topic, isKey, data, avroSchema)
      if (this.autoRegisterSchema) {
        SchemaId.fromInt(this.schemaRegistry.register(subject, avroSchema))
      } else {
        SchemaId.fromInt(this.schemaRegistry.getId(subject, avroSchema))
      }
    } catch {
      case exc: RestClientException =>
        throw new SerializationException(s"Error registering/retrieving Avro schema: " + avroSchema.rawSchema(), exc)
    }
  }

  protected def writeData(data: Any, avroSchema: Schema, schemaId: SchemaId, headers: Headers): Array[Byte] =
    Using.resource(new ByteArrayOutputStream) { out =>
      writeHeader(schemaId, out, headers)

      data match {
        case buffer: ByteBuffer => out.write(buffer.array())
        case array: Array[Byte] => out.write(array)
        case _ =>
          val encoder = encoderToUse(avroSchema, out)
          val writer  = createDatumWriter(avroSchema)
          writer.write(data, encoder)
          encoder.flush()
      }

      out.toByteArray
    }

  protected def encoderToUse(schema: Schema, out: OutputStream): Encoder =
    this.encoderFactory.directBinaryEncoder(out, null)

  protected def writeHeader(schemaId: SchemaId, out: OutputStream, headers: Headers): Unit = {
    out.write(AbstractKafkaSchemaSerDe.MAGIC_BYTE)
    out.write(ByteBuffer.allocate(AbstractKafkaSchemaSerDe.idSize).putInt(schemaId.asInt).array)
  }

}
