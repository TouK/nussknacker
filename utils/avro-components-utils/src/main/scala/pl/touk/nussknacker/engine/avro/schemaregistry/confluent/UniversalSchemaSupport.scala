package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.ValidatedNel
import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.encode.{BestEffortAvroEncoder, ValidationMode}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.{ConfluentAvroMessageFormatter, ConfluentAvroMessageReader, UniversalMessageFormatter, UniversalMessageReader}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentAvroPayloadDeserializer, ConfluentJsonSchemaPayloadDeserializer, ConfluentKafkaAvroSerializer, UniversalSchemaPayloadDeserializer}
import pl.touk.nussknacker.engine.avro.sink.{AvroSinkValueParameterExtractor, JsonSinkValueParameterExtractor}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.json.JsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.json.serde.CirceJsonSerializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.SinkValueParameter

import java.nio.charset.StandardCharsets

object UniversalSchemaSupport {

  def createPayloadDeserializer(schema: ParsedSchema): UniversalSchemaPayloadDeserializer = schema match {
    case _: AvroSchema => new ConfluentAvroPayloadDeserializer(false, false, false, DecoderFactory.get())
    case _: JsonSchema => ConfluentJsonSchemaPayloadDeserializer
    case _ => throw new UnsupportedSchemaType(schema)
  }

  def createSerializer[T](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                          kafkaConfig: KafkaConfig,
                          schemaOpt: Option[RuntimeSchemaData[ParsedSchema]],
                          isKey: Boolean): Serializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)

    val serializer = schemaOpt.map(_.schema) match {
      case Some(schema: AvroSchema) => ConfluentKafkaAvroSerializer(kafkaConfig, schemaRegistryClient, Some(schema), isKey = isKey)
      case Some(schema: JsonSchema) =>
        val circeSerializer = new CirceJsonSerializer(schema.rawSchema())
        new Serializer[T] {
          override def serialize(topic: String, data: T): Array[Byte] = circeSerializer.serialize(data.asInstanceOf[Json])
        }
      case _ => throw new IllegalArgumentException("SchemaData should be defined for universal serializer")
    }
    serializer.asInstanceOf[Serializer[T]]
  }

  def createMessageFormatter(schemaRegistryClient: SchemaRegistryClient): UniversalMessageFormatter = (obj: Any, schema: ParsedSchema) => schema match {
    case _: AvroSchema => new ConfluentAvroMessageFormatter(schemaRegistryClient).asJson(obj)
    case _: JsonSchema => BestEffortJsonEncoder.defaultForTests.encode(obj) //todo
    case _ => throw new UnsupportedSchemaType(schema)
  }

  def createMessageReader(schemaRegistryClient: SchemaRegistryClient): UniversalMessageReader = (jsonObj: Json, schema: ParsedSchema, subject: String) => schema match {
    case schema: AvroSchema => new ConfluentAvroMessageReader(schemaRegistryClient).readJson(jsonObj, schema.rawSchema(), subject)
    case schema: JsonSchema => jsonObj match {
      // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
      case j if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
      case other => other.noSpaces.getBytes(StandardCharsets.UTF_8)
    }
    case _ => throw new UnsupportedSchemaType(schema)
  }

  def typeDefinition(schema: ParsedSchema): TypingResult = schema match {
    case schema: AvroSchema => AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.rawSchema())
    case schema: JsonSchema => JsonSchemaTypeDefinitionExtractor.typeDefinition(schema.rawSchema())
    case _ => throw new UnsupportedSchemaType(schema)
  }

  def extractSinkValueParameter(schema: ParsedSchema)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] = schema match {
    case schema: AvroSchema => new AvroSinkValueParameterExtractor().extract(schema.rawSchema())
    case schema: JsonSchema => new JsonSinkValueParameterExtractor().extract(schema.rawSchema())
    case _ => throw new UnsupportedSchemaType(schema)
  }

  def sinkValueEncoder(schema: ParsedSchema, validationMode: ValidationMode): Any => AnyRef = schema match {
    case schema: AvroSchema => (value: Any) => BestEffortAvroEncoder(validationMode).encodeOrError(value, schema.rawSchema())
    case _: JsonSchema => (value: Any) =>  BestEffortJsonEncoder.defaultForTests.encode(value) //todo
    case _ => throw new UnsupportedSchemaType(schema)
  }
}

class UnsupportedSchemaType(parsedSchema: ParsedSchema) extends IllegalArgumentException(s"Unsupported schema type: ${parsedSchema.schemaType()}")