package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.{ConfluentAvroMessageFormatter, ConfluentAvroMessageReader, UniversalMessageFormatter, UniversalMessageReader}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentAvroPayloadDeserializer, ConfluentKafkaAvroSerializer, UniversalSchemaPayloadDeserializer}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import java.nio.charset.StandardCharsets

object UniversalSchemaSupport {

  def createPayloadDeserializer(schema: ParsedSchema): UniversalSchemaPayloadDeserializer = schema match {
    case _: AvroSchema => new ConfluentAvroPayloadDeserializer(false, false, false, DecoderFactory.get())
    case _ => throw new UnsupportedSchemaType(schema)
  }

  def createSerializer[T](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                          kafkaConfig: KafkaConfig,
                          schemaOpt: Option[RuntimeSchemaData[ParsedSchema]],
                          isKey: Boolean): Serializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)

    val serializer = schemaOpt.map(_.schema) match {
      case Some(schema: AvroSchema) => ConfluentKafkaAvroSerializer(kafkaConfig, schemaRegistryClient, Some(schema), isKey = isKey)
      //todo: add support for json schema
      case Some(other) => throw new UnsupportedSchemaType(other)
      case None => throw new IllegalArgumentException("SchemaData should be defined for universal serializer")
    }
    serializer.asInstanceOf[Serializer[T]]
  }

  def createMessageFormatter(schemaRegistryClient: SchemaRegistryClient): UniversalMessageFormatter = (obj: Any, schema: ParsedSchema) => schema match {
    case _: AvroSchema => new ConfluentAvroMessageFormatter(schemaRegistryClient).asJson(obj)
    //todo: change to JsonSchema
    case s: ParsedSchema if s.schemaType() == "JSON" => ??? //todo: handle json schema
    case _ => throw new UnsupportedSchemaType(schema)
  }

  def createMessageReader(schemaRegistryClient: SchemaRegistryClient): UniversalMessageReader = (jsonObj: Json, schema: ParsedSchema, subject: String) => schema match {
    case schema: AvroSchema => new ConfluentAvroMessageReader(schemaRegistryClient).readJson(jsonObj, schema.rawSchema(), subject)
    //todo: change JsonSchema
    case s: ParsedSchema if s.schemaType() == "JSON" =>
      jsonObj match {
        // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
        case j if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
        case other => other.noSpaces.getBytes(StandardCharsets.UTF_8)
      }
    case _ => throw new UnsupportedSchemaType(schema)
  }

  def typeDefinition(schema: ParsedSchema): TypingResult = schema match {
    case schema: AvroSchema => AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.rawSchema())
    case _ => throw new UnsupportedSchemaType(schema)
  }
}

class UnsupportedSchemaType(parsedSchema: ParsedSchema) extends IllegalArgumentException(s"Unsupported schema type: ${parsedSchema.schemaType()}")