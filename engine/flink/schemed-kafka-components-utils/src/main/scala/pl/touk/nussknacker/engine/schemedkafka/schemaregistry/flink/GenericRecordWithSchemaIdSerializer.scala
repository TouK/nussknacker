package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink

import com.esotericsoftware.kryo.{Kryo, Serializer}
import com.esotericsoftware.kryo.io.{Input, Output}
import org.apache.avro.generic.GenericData
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schema.DatumReaderWriterMixin
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  GenericRecordWithSchemaId,
  IntSchemaId,
  SchemaId,
  SchemaRegistryClient,
  StringSchemaId
}

import java.io.ByteArrayOutputStream
import java.util

/**
 * Flink-compatible serializer with default constructor.
 *
 * During deserialization uses Schema Registry clients registered in [[GenericRecordWithSchemaIdSerializer]] object.
 */
@SerialVersionUID(42553325228495L)
class GenericRecordWithSchemaIdSerializer
    extends Serializer[GenericRecordWithSchemaId](false, false)
    with DatumReaderWriterMixin
    with Serializable {

  import GenericRecordWithSchemaIdSerializer._

  private val stringSchemaMarker: Int = -1

  override def write(kryo: Kryo, output: Output, record: GenericRecordWithSchemaId): Unit = {
    // typically identifiers should be manually assigned, i.e. they should be small positive integers
    output.writeVarInt(record.getSchemaRegistryId, true)
    record.getSchemaId match {
      case IntSchemaId(value) =>
        output.writeVarInt(value, true)
      case StringSchemaId(value) =>
        output.writeVarInt(stringSchemaMarker, true)
        output.writeString(value)
    }

    val bos = new ByteArrayOutputStream()
    serializeRecord(record, bos)
    output.writeVarInt(bos.size(), true)
    output.writeBytes(bos.toByteArray)
  }

  private def serializeRecord(record: GenericRecordWithSchemaId, bos: ByteArrayOutputStream): Unit = {
    val writer  = createDatumWriter(record.getSchema)
    val encoder = EncoderFactory.get().directBinaryEncoder(bos, null)
    writer.write(record, encoder)
  }

  override def read(
      kryo: Kryo,
      input: Input,
      `type`: Class[_ <: GenericRecordWithSchemaId]
  ): GenericRecordWithSchemaId = {
    val schemaRegistryId = input.readVarInt(true)
    val schemaIdInt      = input.readVarInt(true)
    val schemaId = if (schemaIdInt >= 0) {
      SchemaId.fromInt(schemaIdInt)
    } else if (schemaIdInt == stringSchemaMarker) {
      val schemaIdString = input.readString()
      SchemaId.fromString(schemaIdString)
    } else {
      throw new IllegalArgumentException(
        s"Unsupported schemaId format: $schemaIdInt. Should be non-negative integer or -1 for string schemas"
      )
    }

    val lengthOfData          = input.readVarInt(true)
    val recordBytes           = input.readBytes(lengthOfData)
    val recordWithoutSchemaId = deserializeRecord(lengthOfData, schemaRegistryId, schemaId, recordBytes)
    new GenericRecordWithSchemaId(recordWithoutSchemaId, schemaRegistryId, schemaId, false)
  }

  private def deserializeRecord(
      lengthOfData: Int,
      schemaRegistryId: Int,
      schemaId: SchemaId,
      recordBytes: Array[Byte]
  ): GenericData.Record = {
    val parsedSchema  = getSchemaRegistryClientClient(schemaRegistryId).getSchemaById(schemaId).schema
    val writerSchema  = AvroUtils.extractSchema(parsedSchema)
    val reader        = createDatumReader(writerSchema, writerSchema)
    val binaryDecoder = DecoderFactory.get().binaryDecoder(recordBytes, 0, lengthOfData, null)
    reader.read(null, binaryDecoder).asInstanceOf[GenericData.Record]
  }

  override def copy(kryo: Kryo, original: GenericRecordWithSchemaId): GenericRecordWithSchemaId =
    AvroUtils.genericData.deepCopy(original.getSchema, original)

}

object GenericRecordWithSchemaIdSerializer {
  // job initialization in single-threaded, it's safe to have an unsynchronized map here
  private val schemaRegistries = new util.HashMap[Int, SchemaRegistryClient]()

  def register(
      schemaRegistryId: Int,
      schemaRegistryClient: SchemaRegistryClient
  ): Unit = schemaRegistries.put(schemaRegistryId, schemaRegistryClient)

  private def getSchemaRegistryClientClient(schemaRegistryId: Int): SchemaRegistryClient =
    Option(schemaRegistries.get(schemaRegistryId))
      .getOrElse(throw new IllegalStateException(s"Unknown schemaRegistryId: $schemaRegistryId"))

  def clearRegistrations(): Unit = {
    schemaRegistries.clear()
  }

}
