package pl.touk.nussknacker.engine.avro.confluent.formatter

import java.io.PrintStream
import java.nio.ByteBuffer

import io.confluent.kafka.serializers.AbstractKafkaAvroDeserializer
import org.apache.avro.AvroRuntimeException
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.confluent.ConfluentSchemaRegistryClient

/**
  * This class is mainly copy-paste of Confluent's AvroMessageFormatter but with access to writeTo method
  * with bytes instead of record.
  *
  * @param ConfluentSchemaRegistryClient schema registry client
  */
private[formatter] class ConfluentAvroMessageFormatter(confluentSchemaRegistryClient: ConfluentSchemaRegistryClient)
  extends AbstractKafkaAvroDeserializer {

  schemaRegistry = confluentSchemaRegistryClient.confluentSchemaRegistryClient

  private val encoderFactory = EncoderFactory.get

  def writeTo(data: Array[Byte], isKey: Boolean, output: PrintStream): Unit = {
    val obj = deserialize(false, null, isKey, data, null)
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

}
