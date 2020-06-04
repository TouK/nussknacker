package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import java.io.{ByteArrayOutputStream, IOException}
import java.nio.ByteBuffer

import io.confluent.kafka.serializers.{AbstractKafkaAvroSerDe, NonRecordContainer}
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericDatumWriter, GenericRecord}
import org.apache.avro.io.EncoderFactory
import org.apache.avro.reflect.ReflectDatumWriter
import org.apache.avro.specific.{SpecificDatumWriter, SpecificRecord}
import org.apache.kafka.common.errors.SerializationException

/**
  * Abstract confluent serializer class. Serialize algorithm is copy past from AbstractKafkaAvroSerializer.serializeImpl.
  * Serializer try convert data (in most cases it will be GenericRecord) to indicated schema.
  *
  * There is some problem when GenericRecord has different schema then final schema - DatumWriter throws exception,
  * because data could not support field from schema. When this situation has place wy try to convert data to provided schema by
  * setting default value when it is possible.
  *
  */
class AbstractConfluentKafkaAvroSerializer extends AbstractKafkaAvroSerDe {

  import scala.collection.JavaConverters._

  protected val encoderFactory: EncoderFactory = EncoderFactory.get

  def serialize(schema: Schema, schemaId: Int, data: Any): Array[Byte] = {
    if (data == null)
      null
    else {
      try {
        val out = new ByteArrayOutputStream
        out.write(AbstractKafkaAvroSerDe.MAGIC_BYTE)
        out.write(ByteBuffer.allocate(AbstractKafkaAvroSerDe.idSize).putInt(schemaId).array)

        data match {
          case array: Array[Byte] => out.write(array)
          case _ =>
            val encoder = this.encoderFactory.directBinaryEncoder(out, null)

            val value = data match {
              //When record schema is different then provided schema then we try to convert this record to final schema
              case record: GenericRecord if !record.getSchema.equals(schema) => convertRecordToSchema(record, schema)
              case container: NonRecordContainer => container.getValue
              case _ => data
            }

            val writer = value match {
              case _: SpecificRecord => new SpecificDatumWriter[Any](schema)
              case _ if this.useSchemaReflection => new ReflectDatumWriter[Any](schema)
              case _ => new GenericDatumWriter[Any](schema)
            }

            writer.write(value, encoder)
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

  /**
    * Convert serialization record to final schema.
    * We try to set schema default value when record doesn't support field from schema.
    *
    * @param record
    * @param schema
    * @return
    */
  private def convertRecordToSchema(record: GenericRecord, schema: Schema): GenericData.Record = {
    val newRecord = new GenericData.Record(schema)
    val fields = schema.getFields.asScala

    fields.foreach{ field => {
      val recordValue = record.get(field.name())

      val value = recordValue match {
        case null if field.hasDefaultValue => field.defaultVal()
        case _ => recordValue
      }

      newRecord.put(field.name(), value)
    }}

    newRecord
  }
}
