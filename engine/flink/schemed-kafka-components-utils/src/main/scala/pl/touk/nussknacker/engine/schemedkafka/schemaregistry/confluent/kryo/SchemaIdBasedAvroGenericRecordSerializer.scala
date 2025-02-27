package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.kryo

import com.esotericsoftware.kryo.{Kryo, Serializer}
import com.esotericsoftware.kryo.io.{Input, Output}
import com.github.ghik.silencer.silent
import org.apache.avro.generic.GenericData
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import pl.touk.nussknacker.engine.flink.api.serialization.InstanceBasedKryoSerializerRegistrar
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, SchemaRegistryClientKafkaConfig}
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schema.DatumReaderWriterMixin
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry._

import java.io.ByteArrayOutputStream

object SchemaIdBasedAvroGenericRecordSerializer {

  // TODO: We shouldn't use InstanceBasedKryoSerializerRegistrar here. This causes that we can't use any RawType
  //       in table-api components. This happens because RawType become not comparable if there is any instance-based serializer
  //       registered in ExecutionConfig.
  //       See:
  //         - RawType.equals checks serializer.equals(rawType.serializer)
  //         - KryoSerializer.equals checks Objects.equals(defaultSerializers, other.defaultSerializers)
  //         - KryoSerializer.defaultSerializers is a LinkedHashMap<Class<?>, ExecutionConfig.SerializableSerializer<?>>
  //         - SerializableSerializer has equals method not implemented (so it checks reference equality)
  @silent("deprecated")
  def registrar(schemaRegistryClientFactory: SchemaRegistryClientFactory, kafkaConfig: KafkaConfig) = {
    new InstanceBasedKryoSerializerRegistrar(
      new SchemaIdBasedAvroGenericRecordSerializer(
        schemaRegistryClientFactory,
        kafkaConfig.schemaRegistryClientKafkaConfig
      ),
      classOf[GenericRecordWithSchemaId]
    )
  }

}

@SerialVersionUID(42553325228495L)
class SchemaIdBasedAvroGenericRecordSerializer(
    schemaRegistryClientFactory: SchemaRegistryClientFactory,
    schemaRegistryClientKafkaConfig: SchemaRegistryClientKafkaConfig
) extends Serializer[GenericRecordWithSchemaId](false, false)
    with DatumReaderWriterMixin
    with Serializable {

  @transient private lazy val schemaRegistry = schemaRegistryClientFactory.create(schemaRegistryClientKafkaConfig)

  @transient protected lazy val encoderFactory: EncoderFactory = EncoderFactory.get

  @transient protected lazy val decoderFactory: DecoderFactory = DecoderFactory.get

  private val stringSchemaMarker: Int = -1

  override def write(kryo: Kryo, out: Output, record: GenericRecordWithSchemaId): Unit = {
    // Avro decoder during decoding base on information that will occur EOF. Because of this we need to additionally
    // store information about length.
    val bos = new ByteArrayOutputStream()
    writeDataBytes(record, bos)

    out.writeVarInt(bos.size(), true)
    record.getSchemaId match {
      case IntSchemaId(value) =>
        out.writeVarInt(value, true)
      case StringSchemaId(value) =>
        out.writeVarInt(stringSchemaMarker, true)
        out.writeString(value)
    }
    out.writeBytes(bos.toByteArray)
  }

  private def writeDataBytes(record: GenericRecordWithSchemaId, bos: ByteArrayOutputStream): Unit = {
    val writer  = createDatumWriter(record.getSchema)
    val encoder = this.encoderFactory.directBinaryEncoder(bos, null)
    writer.write(record, encoder)
  }

  override def read(kryo: Kryo, input: Input, clazz: Class[GenericRecordWithSchemaId]): GenericRecordWithSchemaId = {
    val lengthOfData = input.readVarInt(true)
    val schemaIdInt  = input.readVarInt(true)
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
    val dataBuffer            = input.readBytes(lengthOfData)
    val recordWithoutSchemaId = readRecord(lengthOfData, schemaId, dataBuffer)
    new GenericRecordWithSchemaId(recordWithoutSchemaId, schemaId, false)
  }

  private def readRecord(lengthOfData: Int, schemaId: SchemaId, dataBuffer: Array[Byte]) = {
    val parsedSchema  = schemaRegistry.getSchemaById(schemaId).schema
    val writerSchema  = AvroUtils.extractSchema(parsedSchema)
    val reader        = createDatumReader(writerSchema, writerSchema)
    val binaryDecoder = decoderFactory.binaryDecoder(dataBuffer, 0, lengthOfData, null)
    reader.read(null, binaryDecoder).asInstanceOf[GenericData.Record]
  }

  override def copy(kryo: Kryo, original: GenericRecordWithSchemaId): GenericRecordWithSchemaId = {
    // deepCopy won't work correctly with LogicalTypes - see GenericData.Record.INSTANCE singleton (without conversions) usage in GenericData.Record
    new GenericRecordWithSchemaId(original, false)
  }

}
