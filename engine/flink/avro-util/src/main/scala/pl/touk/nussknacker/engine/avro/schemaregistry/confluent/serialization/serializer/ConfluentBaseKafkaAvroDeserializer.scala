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
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClient

/**
  * This is Base KafkaAvro Deserializer class. It contains parts of AbstractKafkaAvroDeserializer
  *
  * @param confluentSchemaRegistry
  * @param isKey
  */
abstract class ConfluentBaseKafkaAvroDeserializer[T](confluentSchemaRegistry: ConfluentSchemaRegistryClient, var isKey: Boolean)
  extends AbstractKafkaAvroSerDe with Deserializer[T] with ConfluentKafkaAvroSerialization {

  schemaRegistry = confluentSchemaRegistry.client

  var useSpecificAvroReader: Boolean = false

  //It's copy paste from AvroSchemaUtils, because there this is private
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

  protected def deserializeToSchema(payload: Array[Byte], schema: Schema): T = {
    val buffer = parsePayloadToByteBuffer(payload)
    val data = read(buffer, schema)
    data.asInstanceOf[T]
  }

  protected def schemaByTopicAndVersion(topic: String, version: Option[Int]): Schema =
    schemaByTopicAndVersion(confluentSchemaRegistry, topic, version, isKey)

  def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {
    val deserializerConfig = new KafkaAvroDeserializerConfig(configs)
    configureClientProperties(deserializerConfig)
    useSpecificAvroReader = deserializerConfig.getBoolean("specific.avro.reader")
    this.isKey = isKey
  }

  /**
    * It's copy paste from AbstractKafkaAvroDeserializer#DeserializationContext.read with some modification. We pass
    * there record buffer data and schema which will be used to convert record.
    *
    * @param buffer
    * @param schema
    * @return
    */
  protected def read(buffer: ByteBuffer, schema: Schema): Any = {
    var schemaId = -1

    try {
      schemaId = buffer.getInt
      val recordSchema = schemaRegistry.getById(schemaId)
      val reader = createDatumReader(recordSchema, schema)
      val length = buffer.limit - 1 - AbstractKafkaAvroSerDe.idSize
      if (schema.getType == Type.BYTES) {
        val bytes = new Array[Byte](length)
        buffer.get(bytes, 0, length)
        bytes
      } else {
        val start = buffer.position + buffer.arrayOffset
        val binaryDecoder = decoderFactory.binaryDecoder(buffer.array, start, length, null)
        val result = reader.read(null, binaryDecoder)
        if (schema.getType == Type.STRING) result.toString else result
      }
    } catch {
      case exc: RestClientException =>
        throw new SerializationException(s"Error retrieving Avro schema for id : $schemaId", exc)
      case exc@(_: RuntimeException | _: IOException) =>
        // avro deserialization may throw IOException, AvroRuntimeException, NullPointerException, etc
        throw new SerializationException(s"Error deserializing Avro message for id: $schemaId", exc)
    }
  }

  /**
    * It's modified AvroSchemaUtils.getDatumReader. We pass extracted schema from record there and final schema to
    * which we will convert record.
    *
    * @param actualSchema it's already extracted schema from event record
    * @param exceptedSchema it's a final schema
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
}
