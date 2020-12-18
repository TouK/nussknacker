package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import java.io.IOException
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.AbstractKafkaAvroSerializer
import org.apache.avro.Schema.Type
import org.apache.avro.io.DecoderFactory
import org.apache.avro.util.Utf8
import org.apache.avro.{AvroRuntimeException, Schema}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schema.StringForcingDatumReader
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils

/**
  * This class is mainly copy-paste of Confluent's AvroMessageReader but with better constructor handling
  * both passing schemaRegistryClient and keySeparator.
  *
  * @param schemaRegistryClient schema registry client
  * @param topic topic
  * @param parseKey if key should be parsed
  * @param keySeparator key separator
  */
private[confluent] class ConfluentAvroMessageReader(schemaRegistryClient: SchemaRegistryClient,
                                                    topic: String,
                                                    parseKey: Boolean,
                                                    keySeparator: String)
  extends AbstractKafkaAvroSerializer {

  schemaRegistry = schemaRegistryClient

  private val keySubject = ConfluentUtils.keySubject(topic)

  private val valueSubject = ConfluentUtils.valueSubject(topic)

  private val decoderFactory = DecoderFactory.get

  // TODO: This implementation won't handle separator escaping
  def readMessage(str: String, keySchema: Schema, valueSchema: Schema): ProducerRecord[Array[Byte], Array[Byte]] = {
    try {
      if (!parseKey) {
        val value = jsonToAvro(str, valueSchema)
        val serializedValue = serializeImpl(valueSubject, value)
        new ProducerRecord(topic, serializedValue)
      } else {
        val keyIndex = str.indexOf(keySeparator)
        if (keyIndex < 0) {
          throw new SerializationException("No key found in line " + str)
        } else {
          val keyString = str.substring(0, keyIndex)
          val valueString = if (keyIndex + 1 > str.length) "" else str.substring(keyIndex + 1)
          val key = jsonToAvro(keyString, keySchema)
          val serializedKey = serializeImpl(keySubject, key)
          val value = jsonToAvro(valueString, valueSchema)
          val serializedValue = serializeImpl(valueSubject, value)
          new ProducerRecord(topic, serializedKey, serializedValue)
        }
      }
    } catch {
      case ex: IOException =>
        throw new SerializationException("Error reading from input", ex)
    }
  }

  private def jsonToAvro(jsonString: String, schema: Schema) = {
    try {
      val reader = StringForcingDatumReader.genericDatumReader[AnyRef](schema, schema, AvroUtils.genericData)
      val obj = reader.read(null, decoderFactory.jsonDecoder(schema, jsonString))
      if (schema.getType == Type.STRING)
        obj.asInstanceOf[Utf8].toString
      else
        obj
    } catch {
      case ex: IOException =>
        throw new SerializationException(
          String.format("Error deserializing json %s to Avro of schema %s", jsonString, schema), ex)
      case ex: AvroRuntimeException =>
        throw new SerializationException(
          String.format("Error deserializing json %s to Avro of schema %s", jsonString, schema), ex)
    }
  }

}
