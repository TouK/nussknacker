package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import io.circe.Json
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema.Type
import org.apache.avro.io.DecoderFactory
import org.apache.avro.util.Utf8
import org.apache.avro.Schema
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schema.{DatumReaderWriterMixin, DefaultAvroSchemaEvolution}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.AbstractConfluentKafkaAvroSerializer

import scala.reflect.ClassTag

/**
  * @param schemaRegistryClient schema registry client
  */
private[confluent] class ConfluentAvroMessageReader(schemaRegistryClient: SchemaRegistryClient)
  extends AbstractConfluentKafkaAvroSerializer(new DefaultAvroSchemaEvolution) with DatumReaderWriterMixin {

  schemaRegistry = schemaRegistryClient

  private val decoderFactory = DecoderFactory.get

  def readJson[T: ClassTag](jsonObj: Json, schemaId: Int, schema: Schema): Array[Byte] = {
    try {
      val avroObj = jsonToAvro[T](jsonObj, schema)
      val avroSchema = new AvroSchema(schema)
      writeData(avroObj, avroSchema.rawSchema(), schemaId)
    } catch {
      case ex: Exception =>
        throw new SerializationException("Error reading from input", ex)
    }
  }

  private def jsonToAvro[T: ClassTag](jsonObj: Json, schema: Schema): AnyRef = {
    val jsonString = jsonObj.noSpaces
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
