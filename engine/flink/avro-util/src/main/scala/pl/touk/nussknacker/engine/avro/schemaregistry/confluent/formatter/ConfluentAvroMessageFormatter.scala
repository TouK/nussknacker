package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import io.circe.Json
import io.confluent.kafka.schemaregistry.avro.AvroSchemaUtils
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.AbstractKafkaAvroDeserializer
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.schema.DatumReaderWriterMixin
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

/**
  * @param schemaRegistryClient schema registry client
  */
private[confluent] class ConfluentAvroMessageFormatter(schemaRegistryClient: SchemaRegistryClient) extends AbstractKafkaAvroDeserializer with DatumReaderWriterMixin {

  private val encoderFactory = EncoderFactory.get

  schemaRegistry = schemaRegistryClient

  def getSchemaIdOpt(bytes: Array[Byte]): Option[Int] = {
    bytes match {
      case avroContent if avroContent.nonEmpty => Some(ConfluentUtils.readId(avroContent))
      case _ => None
    }
  }

  def asJson[T: ClassTag](obj: T): Json = {
    val schema = AvroSchemaUtils.getSchema(obj)
    val bos = new ByteArrayOutputStream()
    val output = new PrintStream(bos, true, StandardCharsets.UTF_8.toString)

    try {
      //pretty = false is important, as we rely on the fact that there are no new lines in message parsing
      val encoder = encoderFactory.jsonEncoder(schema, output, false)
      val record = obj match {
        case bytes: Array[Byte] => ByteBuffer.wrap(bytes)
        case other => other
      }
      val writer = createDatumWriter(record, schema, useSchemaReflection = false)
      writer.write(record, encoder)
      encoder.flush()
      val str = bos.toString(StandardCharsets.UTF_8)
      // assume the output of encoder is correct or throw Exception trying
      io.circe.parser.parse(str).right.get
    } catch {
      case ex: Exception =>
        throw new SerializationException(String.format("Error serializing Avro data of schema %s to json", schema), ex)
    }
  }
}
