package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import io.circe.Json
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.AbstractKafkaAvroSerializer
import org.apache.avro.Schema.Type
import org.apache.avro.io.DecoderFactory
import org.apache.avro.util.Utf8
import org.apache.avro.Schema
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schema.DatumReaderWriterMixin
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils

import scala.reflect.ClassTag

/**
  * This class is mainly copy-paste of Confluent's AvroMessageReader but with better constructor handling
  * both passing schemaRegistryClient and keySeparator.
  *
  * @param schemaRegistryClient schema registry client
  * @param topic topic
  */
private[confluent] class ConfluentAvroMessageReader(schemaRegistryClient: SchemaRegistryClient, topic: String)
  extends AbstractKafkaAvroSerializer with DatumReaderWriterMixin {

  schemaRegistry = schemaRegistryClient

  private val decoderFactory = DecoderFactory.get

  def schemaById(schemaId: Int): Schema = {
    val parsedSchema = schemaRegistryClient.getSchemaById(schemaId)
    ConfluentUtils.extractSchema(parsedSchema)
  }

  def readJson[T: ClassTag](jsonObj: Json, schemaOpt: Option[Schema], subject: String): Array[Byte] = {
    try {
      schemaOpt match {
        case None => Array.emptyByteArray
        case Some(schema) =>
          val jsonStr = jsonObj.noSpaces
          val avroObj = jsonToAvro[T](jsonStr, schema)
          val serializedValue = serializeImpl(subject, avroObj, new AvroSchema(schema))
          serializedValue
      }
    } catch {
      case ex: Exception =>
        throw new SerializationException("Error reading from input", ex)
    }
  }

  private def jsonToAvro[T: ClassTag](jsonString: String, schema: Schema) = {
    try {
      val reader = createDatumReader(schema, schema, useSchemaReflection = false, useSpecificAvroReader = AvroUtils.isSpecificRecord[T])
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
