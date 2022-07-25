package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import io.circe.Json
import io.confluent.kafka.formatter.AvroMessageFormatter
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.{AvroSchema, AvroSchemaUtils}
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.AbstractKafkaAvroDeserializer
import org.apache.avro.Schema
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.schema.DatumReaderWriterMixin

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

/**
 * @param schemaRegistryClient schema registry client
 */
private[confluent] class UniversalMessageFormatter(schemaRegistryClient: SchemaRegistryClient) {

  private lazy val avroMessageFormatter = new ConfluentAvroMessageFormatter(schemaRegistryClient)

  def asJson[T: ClassTag](obj: T, parsedSchema: ParsedSchema): Json = parsedSchema match {
    case _: AvroSchema => avroMessageFormatter.asJson(obj)
    //todo: change to JsonSchema
    case s: ParsedSchema if s.schemaType() == "JSON" => ??? //todo: handle json schema
    case s: ParsedSchema  => throw new IllegalArgumentException(s"Unsupported schema type: ${s.schemaType()}")
  }
}
