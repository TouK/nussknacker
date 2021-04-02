package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.kryo

import java.io.ByteArrayOutputStream

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import org.apache.avro.generic.GenericData
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import pl.touk.nussknacker.engine.avro.schema.DatumReaderWriterMixin
import pl.touk.nussknacker.engine.avro.schemaregistry.GenericRecordWithSchemaId
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.flink.api.serialization.SerializerWithSpecifiedClass
import pl.touk.nussknacker.engine.kafka.KafkaConfig

class SchemaIdBasedAvroGenericRecordSerializer(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, kafkaConfig: KafkaConfig)
  extends SerializerWithSpecifiedClass[GenericRecordWithSchemaId](false, false) with DatumReaderWriterMixin {

  @transient private lazy val schemaRegistry = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig).client

  @transient protected lazy val encoderFactory: EncoderFactory = EncoderFactory.get

  @transient protected lazy val decoderFactory: DecoderFactory = DecoderFactory.get

  override def clazz: Class[_] = classOf[GenericRecordWithSchemaId]

  override def write(kryo: Kryo, out: Output, record: GenericRecordWithSchemaId): Unit = {
    // Avro decoder during decoding base on information that will occur EOF. Because of this we need to additionally
    // store information about length.
    val bos = new ByteArrayOutputStream()
    writeDataBytes(record, bos)

    out.writeVarInt(bos.size(), true)
    out.writeVarInt(record.getSchemaId, true)
    out.writeBytes(bos.toByteArray)
  }

  private def writeDataBytes(record: GenericRecordWithSchemaId, bos: ByteArrayOutputStream): Unit = {
    val writer = createDatumWriter(record, record.getSchema, useSchemaReflection = false)
    val encoder = this.encoderFactory.directBinaryEncoder(bos, null)
    writer.write(record, encoder)
  }

  override def read(kryo: Kryo, input: Input, clazz: Class[GenericRecordWithSchemaId]): GenericRecordWithSchemaId = {
    val lengthOfData = input.readVarInt(true)
    val schemaId = input.readVarInt(true)
    val dataBuffer = input.readBytes(lengthOfData)

    val recordWithoutSchemaId = readRecord(lengthOfData, schemaId, dataBuffer)
    new GenericRecordWithSchemaId(recordWithoutSchemaId, schemaId, false)
  }

  private def readRecord(lengthOfData: Int, schemaId: Int, dataBuffer: Array[Byte]) = {
    val parsedSchema = schemaRegistry.getSchemaById(schemaId)
    val writerSchema = ConfluentUtils.extractSchema(parsedSchema)
    val reader = createDatumReader(writerSchema, writerSchema, useSchemaReflection = false, useSpecificAvroReader = false)
    val binaryDecoder = decoderFactory.binaryDecoder(dataBuffer, 0, lengthOfData, null)
    reader.read(null, binaryDecoder).asInstanceOf[GenericData.Record]
  }

  override def copy(kryo: Kryo, original: GenericRecordWithSchemaId): GenericRecordWithSchemaId = {
    // deepCopy won't work correctly with LogicalTypes - see GenericData.Record.INSTANCE singleton (without conversions) usage in GenericData.Record
    new GenericRecordWithSchemaId(original, false)
  }

}
