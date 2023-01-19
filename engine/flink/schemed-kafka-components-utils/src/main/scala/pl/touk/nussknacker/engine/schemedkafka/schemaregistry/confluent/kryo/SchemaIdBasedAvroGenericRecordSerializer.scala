package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import org.apache.avro.generic.GenericData
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import pl.touk.nussknacker.engine.schemedkafka.schema.DatumReaderWriterMixin
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{GenericRecordWithSchemaId, IntSchemaId, SchemaId, StringSchemaId}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.flink.api.serialization.SerializerWithSpecifiedClass
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, SchemaRegistryClientKafkaConfig}

import java.io.ByteArrayOutputStream

object SchemaIdBasedAvroGenericRecordSerializer {
  def apply(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, kafkaConfig: KafkaConfig): SchemaIdBasedAvroGenericRecordSerializer = {
    new SchemaIdBasedAvroGenericRecordSerializer(schemaRegistryClientFactory, kafkaConfig.schemaRegistryClientKafkaConfig)
  }
}

@SerialVersionUID(42553325228496L)
class SchemaIdBasedAvroGenericRecordSerializer(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, schemaRegistryClientKafkaConfig: SchemaRegistryClientKafkaConfig)
  extends SerializerWithSpecifiedClass[GenericRecordWithSchemaId](false, false) with DatumReaderWriterMixin {

  @transient private lazy val schemaRegistry = schemaRegistryClientFactory.create(schemaRegistryClientKafkaConfig)

  @transient protected lazy val encoderFactory: EncoderFactory = EncoderFactory.get

  @transient protected lazy val decoderFactory: DecoderFactory = DecoderFactory.get

  override def clazz: Class[_] = classOf[GenericRecordWithSchemaId]

  override def write(kryo: Kryo, out: Output, record: GenericRecordWithSchemaId): Unit = {
    // Avro decoder during decoding base on information that will occur EOF. Because of this we need to additionally
    // store information about length.
    val bos = new ByteArrayOutputStream()
    writeDataBytes(record, bos)

    out.writeVarInt(bos.size(), true)
    record.getSchemaId match {
      case IntSchemaId(value) =>
        out.writeByte(1)
        out.writeVarInt(value, true)
      case StringSchemaId(value) =>
        out.writeByte(2)
        out.writeString(value)
    }
    out.writeBytes(bos.toByteArray)
  }

  private def writeDataBytes(record: GenericRecordWithSchemaId, bos: ByteArrayOutputStream): Unit = {
    val writer = createDatumWriter(record, record.getSchema, useSchemaReflection = false)
    val encoder = this.encoderFactory.directBinaryEncoder(bos, null)
    writer.write(record, encoder)
  }

  override def read(kryo: Kryo, input: Input, clazz: Class[GenericRecordWithSchemaId]): GenericRecordWithSchemaId = {
    val lengthOfData = input.readVarInt(true)
    val schemaId = input.readByte match {
      case 1 =>
        SchemaId.fromInt(input.readInt(true))
      case 2 =>
        SchemaId.fromString(input.readString())
      case other =>
        throw new IllegalArgumentException(s"Unsupported schema id type: $other")
    }
    val dataBuffer = input.readBytes(lengthOfData)

    val recordWithoutSchemaId = readRecord(lengthOfData, schemaId, dataBuffer)
    new GenericRecordWithSchemaId(recordWithoutSchemaId, schemaId, false)
  }

  private def readRecord(lengthOfData: Int, schemaId: SchemaId, dataBuffer: Array[Byte]) = {
    val parsedSchema = schemaRegistry.getSchemaById(schemaId)
    val writerSchema = ConfluentUtils.extractSchema(parsedSchema.schema)
    val reader = createDatumReader(writerSchema, writerSchema, useSchemaReflection = false, useSpecificAvroReader = false)
    val binaryDecoder = decoderFactory.binaryDecoder(dataBuffer, 0, lengthOfData, null)
    reader.read(null, binaryDecoder).asInstanceOf[GenericData.Record]
  }

  override def copy(kryo: Kryo, original: GenericRecordWithSchemaId): GenericRecordWithSchemaId = {
    // deepCopy won't work correctly with LogicalTypes - see GenericData.Record.INSTANCE singleton (without conversions) usage in GenericData.Record
    new GenericRecordWithSchemaId(original, false)
  }

}
