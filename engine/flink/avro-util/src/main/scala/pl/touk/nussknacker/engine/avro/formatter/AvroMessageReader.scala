package pl.touk.nussknacker.engine.avro.formatter

import java.io.{ByteArrayOutputStream, IOException}
import java.nio.ByteBuffer

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.serializers.{AbstractKafkaAvroSerDe, NonRecordContainer}
import org.apache.avro.Schema.Type
import org.apache.avro.generic.{GenericDatumReader, GenericDatumWriter}
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import org.apache.avro.specific.{SpecificDatumWriter, SpecificRecord}
import org.apache.avro.util.Utf8
import org.apache.avro.{AvroRuntimeException, Schema}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.SerializationException

/**
  * This class is mainly copy-paste of Confluent's AvroMessageReader but with better constructor handling
  * both passing schemaRegistryClient and keySeparator and without dependency to kafka.utils.VerifiableProperties.
  *
  * @param schemaRegistryClient schema registry client
  * @param topic topic
  * @param parseKey if key should be parsed
  * @param keySeparator key separator
  */
private[formatter] class AvroMessageReader(schemaRegistryClient: SchemaRegistryClient, topic: String,
                                           parseKey: Boolean, keySeparator: String)
  extends AbstractKafkaAvroSerDe {

  schemaRegistry = schemaRegistryClient

  private val keySubject = topic + "-key"

  private val valueSubject = topic + "-value"

  private val encoderFactory = EncoderFactory.get

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
      val reader = new GenericDatumReader[AnyRef](schema)
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

  private def serializeImpl(subject: String, obj: AnyRef): Array[Byte] = {
    if (obj == null) {
      null
    } else {
      var schema: Schema = null
      try {
        schema = getSchema(obj)
        val id = schemaRegistry.getId(subject, schema)
        val out = new ByteArrayOutputStream
        out.write(0)
        out.write(ByteBuffer.allocate(4).putInt(id).array)
        obj match {
          case bytes: Array[Byte] => out.write(bytes.asInstanceOf[Array[Byte]])
          case _ =>
            val encoder = encoderFactory.directBinaryEncoder(out, null)
            val value = obj match {
              case container: NonRecordContainer => container.getValue
              case _ => obj
            }
            val writer = if (value.isInstanceOf[SpecificRecord])
              new SpecificDatumWriter[AnyRef](schema)
            else
              new GenericDatumWriter[AnyRef](schema)
            writer.write(value, encoder)
            encoder.flush()
        }
        val bytes = out.toByteArray
        out.close()
        bytes
      } catch {
        case ex@(_: RuntimeException | _: IOException) =>
          throw new SerializationException("Error serializing Avro message", ex)
        case ex: RestClientException =>
          throw new SerializationException("Error retrieving Avro schema: " + schema, ex)
      }
    }
  }

}