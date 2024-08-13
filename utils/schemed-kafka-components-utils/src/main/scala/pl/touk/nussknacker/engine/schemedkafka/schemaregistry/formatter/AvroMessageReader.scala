package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.formatter

import io.circe.Json
import org.apache.avro.Schema
import org.apache.avro.Schema.Type
import org.apache.avro.generic.GenericData
import org.apache.avro.io.{DatumReader, DecoderFactory}
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.api.process.TopicName

private[schemaregistry] class AvroMessageReader(serializer: Serializer[Any]) {

  private val decoderFactory = DecoderFactory.get

  def readJson(jsonObj: Json, schema: Schema, topic: TopicName.ForSource): Array[Byte] = {
    try {
      val avroObj = jsonToAvro(jsonObj, schema)
      serializer.serialize(topic.name, new RecordHeaders(), avroObj)
    } catch {
      case ex: Exception =>
        throw new SerializationException("Error reading from input", ex)
    }
  }

  private def jsonToAvro(jsonObj: Json, schema: Schema): AnyRef = {
    val jsonString = jsonObj.noSpaces
    try {
      val reader: DatumReader[AnyRef] = GenericData.get().createDatumReader(schema).asInstanceOf[DatumReader[AnyRef]]
      val obj                         = reader.read(null, decoderFactory.jsonDecoder(schema, jsonString))
      if (schema.getType == Type.STRING)
        obj.toString
      else
        obj
    } catch {
      case ex: Exception =>
        throw new SerializationException(
          String.format("Error deserializing json %s to Avro of schema %s", jsonString, schema),
          ex
        )
    }
  }

}
