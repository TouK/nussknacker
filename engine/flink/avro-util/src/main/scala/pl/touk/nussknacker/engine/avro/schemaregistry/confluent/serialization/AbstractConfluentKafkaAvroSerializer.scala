package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import java.io.{ByteArrayOutputStream, IOException}
import java.nio.ByteBuffer

import io.confluent.kafka.schemaregistry.avro.{AvroSchema, AvroSchemaUtils}
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.serializers.{AbstractKafkaAvroSerializer, AbstractKafkaSchemaSerDe}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericContainer
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.schema.{AvroSchemaEvolution, DatumReaderWriterMixin}

/**
  * Abstract confluent serializer class. Serialize algorithm is copy past from AbstractKafkaAvroSerializer.serializeImpl.
  * Serializer try convert data (in most cases it will be GenericContainer) to indicated schema.
  *
  * There is some problem when GenericContainer has different schema then final schema - DatumWriter throws exception,
  * because data could not support field from schema. When this situation has place wy try to convert data to provided schema by
  * using AvroSchemaEvolution.alignRecordToSchema implementation.
  */
class AbstractConfluentKafkaAvroSerializer(avroSchemaEvolution: AvroSchemaEvolution) extends AbstractKafkaAvroSerializer with DatumReaderWriterMixin {

  protected val encoderFactory: EncoderFactory = EncoderFactory.get

  def serialize(avroSchemaOpt: Option[AvroSchema], topic: String, data: Any, isKey: Boolean): Array[Byte] = {
    if (data == null) {
      null
    } else {
      val avroSchema = avroSchemaOpt.getOrElse(new AvroSchema(AvroSchemaUtils.getSchema(data, this.useSchemaReflection)))
      try {
        val subject = getSubjectName(topic, isKey, data, avroSchema)
        val schemaId = if (this.autoRegisterSchema) {
          this.schemaRegistry.register(subject, avroSchema)
        } else {
          this.schemaRegistry.getId(subject, avroSchema)
        }

        val record = data match {
          //We try to convert record to provided schema if it's possible
          case record: GenericContainer if avroSchemaOpt.isDefined => avroSchemaEvolution.alignRecordToSchema(record, avroSchema.rawSchema())
          case _ => data
        }

        writeData(record, avroSchema.rawSchema(), schemaId)
      } catch {
        case exc: RestClientException =>
          throw new SerializationException(s"Error registering/retrieving Avro schema: " + avroSchema.rawSchema(), exc)
        case exc@(_: RuntimeException | _: IOException) =>
          throw new SerializationException("Error serializing Avro message", exc)
      }
    }
  }

  protected def writeData(data: Any, avroSchema: Schema, schemaId: Int): Array[Byte] = {
    val out = new ByteArrayOutputStream
    out.write(AbstractKafkaSchemaSerDe.MAGIC_BYTE)
    out.write(ByteBuffer.allocate(AbstractKafkaSchemaSerDe.idSize).putInt(schemaId).array)

    data match {
      case array: Array[Byte] => out.write(array)
      case _ =>
        val encoder = this.encoderFactory.directBinaryEncoder(out, null)
        val writer = createDatumWriter(data, avroSchema, useSchemaReflection = useSchemaReflection)
        writer.write(data, encoder)
        encoder.flush()
    }

    val bytes = out.toByteArray
    out.close()
    bytes
  }
}
