package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import io.circe.Json
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.AbstractKafkaAvroSerializer
import org.apache.avro.Schema
import org.apache.avro.Schema.Type
import org.apache.avro.generic.GenericData
import org.apache.avro.io.{DatumReader, DecoderFactory}
import org.apache.avro.util.Utf8
import org.apache.kafka.common.errors.SerializationException

/**
  * @param schemaRegistryClient schema registry client
  */
private[confluent] class ConfluentAvroMessageReader(schemaRegistryClient: SchemaRegistryClient)
  extends AbstractKafkaAvroSerializer {

  schemaRegistry = schemaRegistryClient

  private val decoderFactory = DecoderFactory.get

  def readJson(jsonObj: Json, schema: Schema, subject: String): Array[Byte] = {
    try {
      val avroObj = jsonToAvro(jsonObj, schema)
      serializeImpl(subject, avroObj, new AvroSchema(schema))
    } catch {
      case ex: Exception =>
        throw new SerializationException("Error reading from input", ex)
    }
  }

  private def jsonToAvro(jsonObj: Json, schema: Schema): AnyRef = {
    val jsonString = jsonObj.noSpaces
    try {
      val reader: DatumReader[AnyRef] = GenericData.get().createDatumReader(schema).asInstanceOf[DatumReader[AnyRef]]
      val obj = reader.read(null, decoderFactory.jsonDecoder(schema, jsonString))
      if (schema.getType == Type.STRING)
        obj.asInstanceOf[Utf8].toString
      else
        obj
    } catch {
      case ex: Exception =>
        throw new SerializationException(
          String.format("Error deserializing json %s to Avro of schema %s", jsonString, schema), ex)
    }
  }

}
