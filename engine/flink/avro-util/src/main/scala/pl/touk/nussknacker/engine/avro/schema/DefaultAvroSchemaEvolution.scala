package pl.touk.nussknacker.engine.avro.schema

import org.apache.avro.Schema
import org.apache.avro.generic._
import org.apache.avro.io.{DatumReader, DecoderFactory, EncoderFactory}
import pl.touk.nussknacker.engine.avro.{AvroUtils, RuntimeSchemaData}

import java.io.{ByteArrayOutputStream, IOException}
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
class DefaultAvroSchemaEvolution extends AvroSchemaEvolution with DatumReaderWriterMixin with RecordDeserializer {

  /**
    * In future we can try to configure it
    */
  protected final  val useSchemaReflection = false

  protected final val encoderFactory: EncoderFactory = EncoderFactory.get

  override protected final val decoderFactory = DecoderFactory.get

  override def alignRecordToSchema(record: GenericContainer, schema: Schema): Any = {
    val writerSchema = record.getSchema
    if (writerSchema.equals(schema)) {
      record
    } else {
      val serializedObject = serializeRecord(record)
      deserializePayloadToSchema(serializedObject, writerSchema, schema)
    }
  }

  override def canBeEvolved(record: GenericContainer, schema: Schema): Boolean =
    Try(alignRecordToSchema(record, schema)).isSuccess
  
  /**
    * It's copy paste from AbstractKafkaAvroDeserializer#DeserializationContext.read with some modification.
    * We pass there record buffer data and schema which will be used to convert record.
    */
  protected def deserializePayloadToSchema(payload: Array[Byte], writerSchema: Schema, readerSchema: Schema): Any = {
    try {
      // We always want to create generic record at the end, because speecific can has other fields than expected
      val reader = StringForcingDatumReaderProvider.genericDatumReader[AnyRef](writerSchema, readerSchema, AvroUtils.genericData).asInstanceOf[DatumReader[AnyRef]]
      val buffer = ByteBuffer.wrap(payload)
      deserializeRecord(RuntimeSchemaData(readerSchema, None), reader, buffer, 0)
    } catch {
      case exc@(_: RuntimeException | _: IOException) =>
        // avro deserialization may throw IOException, AvroRuntimeException, NullPointerException, etc
        throw new AvroSchemaEvolutionException(s"Error at deserialization payload to record.", exc)
    }
  }

  // Currently schema evolution doesn't support schema id serialization. We assume that it is used only on the end of process,
  // when there won't be any subsequent serializations done
  override protected def schemaIdSerializationEnabled: Boolean = false

  /**
    * Record serialization method, kind of copy paste from AbstractKafkaAvroSerializer#DeserializationContext.read.
    * We use confluent serialization mechanism without some specifics features like:
    *
    * - fetching schema from registry
    * - fetching schema Id
    * - we don't serialize MagicByte and version
    *
    * To serialization we use schema from record.
    */
  protected def serializeRecord(record: GenericContainer): Array[Byte] = {
    try {
      val out = new ByteArrayOutputStream
      val encoder = encoderFactory.directBinaryEncoder(out, null)
      val writer = createDatumWriter(record.getSchema, useSchemaReflection = useSchemaReflection, AvroUtils.isSpecificRecord(record))
      writer.write(record, encoder)
      encoder.flush()
      val bytes = out.toByteArray
      out.close()
      bytes
    } catch {
      case exc@(_: RuntimeException | _: IOException) =>
        // avro serialization may throw IOException, AvroRuntimeException, NullPointerException, etc
        throw new AvroSchemaEvolutionException(s"Error at serialization record to payload.", exc)
    }
  }
}
