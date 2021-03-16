package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import java.io.PrintStream
import java.nio.ByteBuffer

import io.confluent.kafka.schemaregistry.avro.AvroSchemaUtils
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.{AbstractKafkaAvroDeserializer}
import org.apache.avro.AvroRuntimeException
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.common.errors.SerializationException

/**
  * This class is mainly copy-paste of Confluent's AvroMessageFormatter but with access to writeTo method
  * with bytes instead of record.
  *
  * @param schemaRegistryClient schema registry client
  */
private[confluent] class ConfluentAvroMessageFormatter(schemaRegistryClient: SchemaRegistryClient) extends AbstractKafkaAvroDeserializer {

  private val encoderFactory = EncoderFactory.get

  schemaRegistry = schemaRegistryClient

  def writeTo(data: Array[Byte], output: PrintStream): Unit = {
    val obj = deserialize(data)
    val schema = AvroSchemaUtils.getSchema(obj)

    try {
      //pretty = false is important, as we rely on the fact that there are no new lines in message parsing
      val encoder = encoderFactory.jsonEncoder(schema, output, false)
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
        throw new SerializationException(String.format("Error serializing Avro data of schema %s to json", schema), ex)
    }
  }
}
