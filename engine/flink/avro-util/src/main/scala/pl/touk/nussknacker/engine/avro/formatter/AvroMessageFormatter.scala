package pl.touk.nussknacker.engine.avro.formatter

import java.io.{IOException, PrintStream}
import java.nio.ByteBuffer

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDe
import org.apache.avro.AvroRuntimeException
import org.apache.avro.Schema.Type
import org.apache.avro.generic.{GenericDatumReader, GenericDatumWriter}
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import org.apache.kafka.common.errors.SerializationException

/**
  * This class is mainly copy-paste of Confluent's AvroMessageFormatter but with access to writeTo method
  * with bytes instead of record and without dependency to kafka.utils.VerifiableProperties.
  *
  * @param schemaRegistryClient schema registry client
  */
private[formatter] class AvroMessageFormatter(schemaRegistryClient: SchemaRegistryClient)
  extends AbstractKafkaAvroSerDe {

  schemaRegistry = schemaRegistryClient

  private val decoderFactory = DecoderFactory.get

  private val encoderFactory = EncoderFactory.get

  def writeTo(data: Array[Byte], isKey: Boolean, output: PrintStream): Unit = {
    val obj = deserialize(isKey, data)
    val schema = getSchema(obj)
    try {
      val encoder = encoderFactory.jsonEncoder(schema, output)
      val writer = new GenericDatumWriter[AnyRef](schema)
      obj match {
        case bytes: Array[Byte] =>
          writer.write(ByteBuffer.wrap(bytes.asInstanceOf[Array[Byte]]), encoder)
        case _ =>
          writer.write(obj, encoder)
      }
      encoder.flush()
    } catch {
      case ex: AvroRuntimeException =>
        throw new SerializationException(
          String.format("Error serializing Avro data of schema %s to json", schema), ex)
    }
  }

  private def deserialize(isKey: Boolean, payload: Array[Byte]): AnyRef = {
    if (payload == null) {
      null
    } else {
      var id = -1
      try {
        val buffer = getByteBuffer(payload)
        id = buffer.getInt
        val schema = schemaRegistry.getById(id)
        val length = buffer.limit - 1 - 4
        if (schema.getType == Type.BYTES) {
          val bytes = new Array[Byte](length)
          buffer.get(bytes, 0, length)
          bytes
        } else {
          val start = buffer.position + buffer.arrayOffset
          val reader = new GenericDatumReader[AnyRef](schema)
          val obj = reader.read(null, decoderFactory.binaryDecoder(buffer.array, start, length, null))
          if (schema.getType == Type.STRING)
            obj.toString
          else
            obj
        }
      } catch {
        case ex@(_: RuntimeException | _: IOException) =>
          throw new SerializationException("Error deserializing Avro message for id " + id, ex)
        case ex: RestClientException =>
          throw new SerializationException("Error retrieving Avro schema for id " + id, ex)
      }
    }
  }

  private def getByteBuffer(payload: Array[Byte]) = {
    val buffer = ByteBuffer.wrap(payload)
    if (buffer.get != 0) throw new SerializationException("Unknown magic byte!")
    else buffer
  }

}
