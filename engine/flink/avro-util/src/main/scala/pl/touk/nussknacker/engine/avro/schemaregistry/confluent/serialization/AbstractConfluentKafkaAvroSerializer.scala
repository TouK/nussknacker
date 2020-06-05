package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import java.io.{ByteArrayOutputStream, IOException}
import java.nio.ByteBuffer

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDe
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericContainer, GenericDatumWriter}
import org.apache.avro.io.EncoderFactory
import org.apache.avro.reflect.ReflectDatumWriter
import org.apache.avro.specific.{SpecificDatumWriter, SpecificRecord}
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.schema.{AvroSchemaEvolution, DatumReaderWriterMixin}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils

/**
  * Abstract confluent serializer class. Serialize algorithm is copy past from AbstractKafkaAvroSerializer.serializeImpl.
  * Serializer try convert data (in most cases it will be GenericContainer) to indicated schema.
  *
  * There is some problem when GenericContainer has different schema then final schema - DatumWriter throws exception,
  * because data could not support field from schema. When this situation has place wy try to convert data to provided schema by
  * using AvroSchemaEvolution.alignRecordToSchema implementation.
  */
class AbstractConfluentKafkaAvroSerializer(avroSchemaEvolution: AvroSchemaEvolution) extends AbstractKafkaAvroSerDe with DatumReaderWriterMixin {

  protected val encoderFactory: EncoderFactory = EncoderFactory.get

  def serialize(schema: Schema, schemaId: Int, data: Any): Array[Byte] = {
    if (data == null)
      null
    else {
      try {
        val out = new ByteArrayOutputStream
        out.write(ConfluentUtils.MagicByte)
        out.write(ByteBuffer.allocate(ConfluentUtils.IdSize).putInt(schemaId).array)

        data match {
          case array: Array[Byte] => out.write(array)
          case _ =>
            val encoder = this.encoderFactory.directBinaryEncoder(out, null)

            val record = data match {
              //When record schema is different then provided schema then we try to convert this record to final schema
              case record: GenericContainer => avroSchemaEvolution.alignRecordToSchema(record, schema)
              case _ => data
            }

            val writer = createDatumWriter(record, schema, useSchemaReflection = useSchemaReflection)

            writer.write(record, encoder)
            encoder.flush()
        }

        val bytes = out.toByteArray
        out.close()
        bytes
      } catch {
        case exc@(_: RuntimeException | _: IOException) =>
          throw new SerializationException("Error serializing Avro message", exc)
      }
    }
  }
}
