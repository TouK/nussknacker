package pl.touk.nussknacker.engine.avro.schema

import java.io.{ByteArrayOutputStream, IOException}
import java.nio.ByteBuffer

import org.apache.avro.Schema
import org.apache.avro.generic._
import org.apache.avro.io.{DecoderFactory, EncoderFactory}

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
class DefaultAvroSchemaEvolution extends AvroSchemaEvolution with DatumReaderWriterMixin {

  /**
    * In future we can try to configure it
    */
  protected final  val useSchemaReflection = false

  protected final val encoderFactory: EncoderFactory = EncoderFactory.get

  protected final val decoderFactory = DecoderFactory.get

  override def alignRecordToSchema(container: GenericContainer, schema: Schema): GenericContainer = {
    val writerSchema = container.getSchema
    if (writerSchema.equals(schema)) {
      container
    } else {
      val serializedObject = serializeToRecordSchema(container, writerSchema)
      val deserializedRecord = deserializePayloadToSchema(serializedObject, container, writerSchema, schema)
      deserializedRecord.asInstanceOf[GenericContainer]
    }
  }

  /**
    * It's copy paste from AbstractKafkaAvroDeserializer#DeserializationContext.read with some modification.
    * We pass there record buffer data and schema which will be used to convert record.
    *
    * @param payload
    * @param record
    * @param writerSchema
    * @param readerSchema
    * @return
    */
  protected def deserializePayloadToSchema(payload: Array[Byte], record: GenericContainer, writerSchema: Schema, readerSchema: Schema): Any = {
    val reader = createDatumReader(record, writerSchema, readerSchema, useSchemaReflection)

    try {
      val buffer = ByteBuffer.wrap(payload)
      val length = buffer.limit
      if (writerSchema.getType == Schema.Type.BYTES) {
        val bytes = new Array[Byte](length)
        buffer.get(bytes, 0, length)
        bytes
      } else {
        val start = buffer.position + buffer.arrayOffset
        val binaryDecoder = decoderFactory.binaryDecoder(buffer.array, start, length, null)
        val result = reader.read(record, binaryDecoder)
        if (writerSchema.getType == Schema.Type.STRING) result.toString else result
      }
    } catch {
      case exc@(_: RuntimeException | _: IOException) =>
        // avro deserialization may throw IOException, AvroRuntimeException, NullPointerException, etc
        throw new AvroSchemaEvolutionException(s"Error at deserialization payload to record.", exc)
    }
  }

  /**
    * Record serialization method, kind of copy paste from AbstractKafkaAvroSerializer#DeserializationContext.read.
    * We use confluent serialization mechanism without some specifics features like:
    *
    * - fetching schema from registry
    * - fetching schema Id
    * - we don't serialize MagicByte and version
    *
    * To serialization we use schema from record.
    *
    * @param record
    * @param writerSchema
    * @return
    */
  protected def serializeToRecordSchema(record: GenericContainer, writerSchema: Schema): Array[Byte] = {
    try {
      val out = new ByteArrayOutputStream
      val encoder = encoderFactory.directBinaryEncoder(out, null)
      val writer = createDatumWriter(record, writerSchema, useSchemaReflection = useSchemaReflection)

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
