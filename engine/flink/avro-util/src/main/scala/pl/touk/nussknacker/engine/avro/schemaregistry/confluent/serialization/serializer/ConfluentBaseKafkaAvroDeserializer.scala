package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.serializer

import java.io.IOException
import java.nio.ByteBuffer
import java.util

import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.serializers.{AbstractKafkaAvroSerDe, KafkaAvroDeserializerConfig}
import org.apache.avro.Schema
import org.apache.avro.Schema.Type
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.io.{DatumReader, DecoderFactory}
import org.apache.avro.reflect.ReflectDatumReader
import org.apache.avro.specific.SpecificDatumReader
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClient

/**
  * @TODO: After update to newer version of confluent we should try extend by AbstractKafkaAvroDeserializer
  *
  * @param confluentSchemaRegistry
  * @param isKey
  */
abstract class ConfluentBaseKafkaAvroDeserializer(confluentSchemaRegistry: ConfluentSchemaRegistryClient, var isKey: Boolean)
  extends AbstractKafkaAvroSerDe {

  schemaRegistry = confluentSchemaRegistry.client

  var useSpecificAvroReader: Boolean = false

  //It's copy paste from AvroSchemaUtils
  lazy val primitives: Map[String, Schema] = {
    val parser = new Schema.Parser
    parser.setValidateDefaults(false)

    Map(
      "Null" -> createPrimitiveSchema(parser, "null"),
      "Boolean" -> createPrimitiveSchema(parser, "boolean"),
      "Integer" -> createPrimitiveSchema(parser, "int"),
      "Long" -> createPrimitiveSchema(parser, "long"),
      "Float" -> createPrimitiveSchema(parser, "float"),
      "Double" -> createPrimitiveSchema(parser, "double"),
      "String" -> createPrimitiveSchema(parser, "string"),
      "Bytes" -> createPrimitiveSchema(parser, "bytes")
    )
  }

  protected lazy val decoderFactory: DecoderFactory = DecoderFactory.get()

  def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {
    val deserializerConfig = new KafkaAvroDeserializerConfig(configs)
    configureClientProperties(deserializerConfig)
    useSpecificAvroReader = deserializerConfig.getBoolean("specific.avro.reader")
    this.isKey = isKey
  }

  protected def deserialize(payload: Array[Byte], exceptedSchema: Schema): Any = {
    val buffer = AvroUtils
      .parsePayloadToByteBuffer(payload)
      .valueOr(exc => throw new SerializationException(exc.getMessage, exc))

    val recordSchema = schemaFromRegistry(buffer.getInt)
    read(buffer, recordSchema, exceptedSchema)
  }

  /**
    * It's copy paste from AbstractKafkaAvroDeserializer#DeserializationContext.read
    *
    * @param buffer
    * @param recordSchema
    * @param exceptedSchema
    * @return
    */
  protected def read(buffer: ByteBuffer, recordSchema: Schema, exceptedSchema: Schema): Any = {
    val reader = createDatumReader(recordSchema, exceptedSchema)

    val length = buffer.limit - 1 - 4
    if (exceptedSchema.getType == Type.BYTES) {
      val bytes = new Array[Byte](length)
      buffer.get(bytes, 0, length)
      bytes
    } else {
      val start = buffer.position + buffer.arrayOffset
      try {
        val binaryDecoder = decoderFactory.binaryDecoder(buffer.array, start, length, null)
        val result = reader.read(null, binaryDecoder)
        if (exceptedSchema.getType == Type.STRING) result.toString else result
      } catch {
        case exc@(_: RestClientException | _: IOException) =>
          throw new SerializationException(s"Error deserializing Avro message for id: ${buffer.getInt}", exc)
      }
    }
  }

  /**
    * It's copy paste from AbstractKafkaAvroDeserializer.getDatumReader
    *
    * @TODO: We should remove it after update confluent to newer version
    *       and using AbstractKafkaAvroDeserializer.getDatumReader
    *
    * @param actualSchema
    * @param exceptedSchema
    * @return
    */
  private def createDatumReader(actualSchema: Schema, exceptedSchema: Schema): DatumReader[Any] = {
    val writerSchemaIsPrimitive = primitives.values.exists(_.equals(exceptedSchema))

    if (useSchemaReflection && !writerSchemaIsPrimitive) {
      new ReflectDatumReader(actualSchema, exceptedSchema)
    } else if(useSpecificAvroReader && !writerSchemaIsPrimitive) {
      new SpecificDatumReader(actualSchema, exceptedSchema)
    } else {
      new GenericDatumReader(actualSchema, exceptedSchema)
    }
  }

  /**
    * It's copy paste from AvroSchemaUtils.createPrimitiveSchema
    *
    * @TODO: We should remove it after update confluent to newer version
    *       and using AbstractKafkaAvroDeserializer.getDatumReader
    *
    * @param parser
    * @param `type`
    * @return
    */
  private def createPrimitiveSchema(parser: Schema.Parser, `type`: String) = {
    val schemaString = String.format("{\"type\" : \"%s\"}", `type`)
    parser.parse(schemaString)
  }

  private def schemaFromRegistry(schemaId: Int): Schema = {
    try {
      schemaRegistry.getById(schemaId)
    } catch {
      case exc@(_: RestClientException | _: IOException) =>
        throw new SerializationException("Error retrieving Avro schema for id " + schemaId, exc)
    }
  }
}
