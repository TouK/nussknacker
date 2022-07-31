package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.ValidatedNel
import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer.SinkValueParamName
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.encode.{AvroSchemaOutputValidator, BestEffortAvroEncoder, BestEffortJsonSchemaEncoder, JsonSchemaOutputValidator, ValidationMode}
import pl.touk.nussknacker.engine.avro.schema.DefaultAvroSchemaEvolution
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{AvroSchemaWithJsonPayload, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.{ConfluentAvroMessageFormatter, ConfluentAvroMessageReader, UniversalMessageFormatter, UniversalMessageReader}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload.JsonPayloadKafkaSerializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization._
import pl.touk.nussknacker.engine.avro.sink.AvroSinkValueParameter
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.json.{JsonSchemaTypeDefinitionExtractor, JsonSinkValueParameter}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.engine.util.output.OutputValidatorError
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.SinkValueParameter

import java.nio.charset.StandardCharsets

object UniversalSchemaSupport {

  def createPayloadDeserializer(schema: ParsedSchema): UniversalSchemaPayloadDeserializer = schema match {
    case _: AvroSchema => new ConfluentAvroPayloadDeserializer(false, false, false, DecoderFactory.get())
    case _: AvroSchemaWithJsonPayload => ConfluentJsonPayloadDeserializer
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
      case Some(schema: AvroSchemaWithJsonPayload) => new JsonPayloadKafkaSerializer(kafkaConfig, schemaRegistryClient, new DefaultAvroSchemaEvolution, Some(schema.avroSchema), isKey = isKey)
      case Some(_: JsonSchema) =>
        new Serializer[T] {
          override def serialize(topic: String, data: T): Array[Byte] = data match {
            case j: Json => j.noSpaces.getBytes()
            case _ => throw new SerializationException(s"Expecting json but got: $data")
          }
        }
      case Some(schema) => throw new UnsupportedSchemaType(schema)
      case None => throw new IllegalArgumentException("SchemaData should be defined for universal serializer")
    }
    serializer.asInstanceOf[Serializer[T]]
  }

  def createMessageFormatter(schemaRegistryClient: SchemaRegistryClient): UniversalMessageFormatter = (obj: Any, schema: ParsedSchema) => schema match {
    case _: AvroSchema => new ConfluentAvroMessageFormatter(schemaRegistryClient).asJson(obj)
    case _: JsonSchema | _: AvroSchemaWithJsonPayload => BestEffortJsonEncoder(failOnUnkown = false, classLoader = getClass.getClassLoader).encode(obj)
    case _ => throw new UnsupportedSchemaType(schema)
  }

  def createMessageReader(schemaRegistryClient: SchemaRegistryClient): UniversalMessageReader = (jsonObj: Json, schema: ParsedSchema, subject: String) => schema match {
    case schema: AvroSchema => new ConfluentAvroMessageReader(schemaRegistryClient).readJson(jsonObj, schema.rawSchema(), subject)
    case _: JsonSchema | _: AvroSchemaWithJsonPayload => jsonObj match {
      // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
      case j if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
      case other => other.noSpaces.getBytes(StandardCharsets.UTF_8)
    }
    case _ => throw new UnsupportedSchemaType(schema)
  }

  def typeDefinition(schema: ParsedSchema): TypingResult = schema match {
    case schema: AvroSchemaWithJsonPayload => AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.rawSchema())
    case schema: AvroSchema => AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.rawSchema())
    case schema: JsonSchema => JsonSchemaTypeDefinitionExtractor.typeDefinition(schema.rawSchema())
    case _ => throw new UnsupportedSchemaType(schema)
  }

  def extractSinkValueParameter(schema: ParsedSchema)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] = schema match {
    case schema: AvroSchema => AvroSinkValueParameter(schema.rawSchema())
    case schema: AvroSchemaWithJsonPayload => AvroSinkValueParameter(schema.rawSchema())
    case schema: JsonSchema => JsonSinkValueParameter(schema.rawSchema(), defaultParamName = SinkValueParamName)
    case _ => throw new UnsupportedSchemaType(schema)
  }

  def sinkValueEncoder(schema: ParsedSchema, validationMode: ValidationMode): Any => AnyRef = schema match {
    case schema: AvroSchema => (value: Any) => BestEffortAvroEncoder(validationMode).encodeOrError(value, schema.rawSchema())
    case schema: AvroSchemaWithJsonPayload => (value: Any) => BestEffortAvroEncoder(validationMode).encodeOrError(value, schema.rawSchema())
    case schema: JsonSchema => (value: Any) =>
      new BestEffortJsonSchemaEncoder(ValidationMode.lax) //todo: pass real validation mode, when BestEffortJsonSchemaEncoder supports it
        .encodeOrError(value, schema.rawSchema())
    case _ => throw new UnsupportedSchemaType(schema)
  }

  def rawOutputValidator(schema: ParsedSchema)(implicit nodeId: NodeId): (TypingResult, ValidationMode) => ValidatedNel[OutputValidatorError, Unit] = (t: TypingResult, validationMode: ValidationMode) =>
    schema match {
      case schema: AvroSchema => new AvroSchemaOutputValidator(validationMode).validateTypingResultToSchema(t, schema.rawSchema())
      case AvroSchemaWithJsonPayload(avroSchema) => new AvroSchemaOutputValidator(validationMode).validateTypingResultToSchema(t, avroSchema.rawSchema())
      case schema: JsonSchema => new JsonSchemaOutputValidator(validationMode).validateTypingResultToSchema(t, schema.rawSchema())
      case _ => throw new UnsupportedSchemaType(schema)
    }
}

class UnsupportedSchemaType(parsedSchema: ParsedSchema) extends IllegalArgumentException(s"Unsupported schema type: ${parsedSchema.schemaType()}")