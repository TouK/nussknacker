package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import java.io.IOException
import java.nio.charset.StandardCharsets

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.AbstractKafkaAvroSerializer
import org.apache.avro.Schema.Type
import org.apache.avro.io.DecoderFactory
import org.apache.avro.util.Utf8
import org.apache.avro.{AvroRuntimeException, Schema}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schema.StringForcingDatumReaderProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils

/**
  * This class is mainly copy-paste of Confluent's AvroMessageReader but with better constructor handling
  * both passing schemaRegistryClient and keySeparator.
  *
  * @param schemaRegistryClient schema registry client
  * @param topic topic
  * @param keySeparator key separator
  */
private[confluent] class ConfluentAvroMessageReader(schemaRegistryClient: SchemaRegistryClient,
                                                    topic: String,
                                                    useStringForKey: Boolean,
                                                    keySeparator: String)
  extends AbstractKafkaAvroSerializer {

  schemaRegistry = schemaRegistryClient

  private val keySubject = ConfluentUtils.keySubject(topic)

  private val valueSubject = ConfluentUtils.valueSubject(topic)

  private val decoderFactory = DecoderFactory.get

  // TODO: This implementation won't handle separator escaping
  def readMessage(str: String, keySchema: Option[Schema], valueSchema: Option[Schema]): ConsumerRecord[Array[Byte], Array[Byte]] = {
    try {
      val keyIndex = str.indexOf(keySeparator)
      if (keyIndex < 0) {
        throw new SerializationException("No key found in line " + str)
      } else {
        val keyString = str.substring(0, keyIndex)
        val valueString = if (keyIndex + 1 > str.length) "" else str.substring(keyIndex + 1)
        if (keySchema.isDefined && keyString.length == 0) {
          // key schema is provided but there is no content
          throw new SerializationException("No key content found in line " + str)
        } else if (keySchema.isEmpty) {
          // handle empty key when key parsing is enabled
          val value = jsonToAvro(valueString, valueSchema.get)
          val serializedValue = serializeImpl(valueSubject, value, new AvroSchema(valueSchema.get))
          new ConsumerRecord(topic, 0, 0L, Array[Byte](), serializedValue)
        }
        else {
          // default scenario when key and value is provided
          val serializedKey = if (useStringForKey) {
            keyString.getBytes(StandardCharsets.UTF_8)
          } else {
            val key = jsonToAvro(keyString, keySchema.get)
            serializeImpl(keySubject, key, new AvroSchema(keySchema.get))
          }
          val value = jsonToAvro(valueString, valueSchema.get)
          val serializedValue = serializeImpl(valueSubject, value, new AvroSchema(valueSchema.get))
          new ConsumerRecord(topic, 0, 0L, serializedKey, serializedValue)
        }
      }
    } catch {
      case ex: IOException =>
        throw new SerializationException("Error reading from input", ex)
    }
  }

  private def jsonToAvro(jsonString: String, schema: Schema) = {
    try {
      val reader = StringForcingDatumReaderProvider.genericDatumReader[AnyRef](schema, schema, AvroUtils.genericData)
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
