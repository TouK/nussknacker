package pl.touk.nussknacker.engine.schemedkafka.schema

import io.confluent.kafka.serializers.NonRecordContainer
import org.apache.avro.Schema
import org.apache.avro.generic._
import org.apache.avro.io.DecoderFactory
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils

import java.io.IOException
import java.nio.ByteBuffer
import scala.util.Try

/**
  * It's base implementation of AvroSchemaEvolution. In this case strategy to evolve record to schema is as follows:
  *
  * serialize record to record schema -> deserialize record to provided schema (final schema)
  *
  * This strategy is based on Confluent implementation of: serialization and deserialization method. But we don't
  * allocate bytes for MagicByte and Id, because we don't need it.
  *
  * For now it's easiest way to convert GenericContainer record to wanted schema.
  */
class DefaultAvroSchemaEvolution extends AvroSchemaEvolution with DatumReaderWriterMixin  {

  private def recordDeserializer = new AvroRecordDeserializer(DecoderFactory.get())

  override def canBeEvolved(record: GenericContainer, schema: Schema): Boolean =
    Try(alignRecordToSchema(record, schema)).isSuccess

  override def alignRecordToSchema(record: GenericContainer, schema: Schema): GenericContainer = {
    val writerSchema = record.getSchema
    if (writerSchema.equals(schema)) {
      record
    } else {
      val serializedObject = AvroUtils.serializeContainerToBytesArray(record)
      deserializePayloadToSchema(serializedObject, writerSchema, schema)
    }
  }

  /**
    * It's copy paste from AbstractKafkaAvroDeserializer#DeserializationContext.read with some modification.
    * We pass there record buffer data and schema which will be used to convert record.
    */
   private def deserializePayloadToSchema(payload: Array[Byte], writerSchema: Schema, readerSchema: Schema): GenericContainer = {
    try {
      // We always want to create generic record at the end, because specific can has other fields than expected
      val reader = AvroUtils.createGenericDatumReader[AnyRef](writerSchema, readerSchema)
      val buffer = ByteBuffer.wrap(payload)
      val data = recordDeserializer.deserializeRecord(readerSchema, reader, buffer)
      data match {
        case c: GenericContainer => c
        case _ => new NonRecordContainer(readerSchema, data)
      }
    } catch {
      case exc@(_: RuntimeException | _: IOException) =>
        // avro deserialization may throw IOException, AvroRuntimeException, NullPointerException, etc
        throw new AvroSchemaEvolutionException(s"Error at deserialization payload to record.", exc)
    }
  }

}
