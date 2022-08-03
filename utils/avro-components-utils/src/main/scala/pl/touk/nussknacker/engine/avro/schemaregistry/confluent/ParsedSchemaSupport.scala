package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.ValidatedNel
import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer.SinkValueParamName
import pl.touk.nussknacker.engine.avro.encode._
import pl.touk.nussknacker.engine.avro.schema.DefaultAvroSchemaEvolution
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{AvroSchemaWithJsonPayload, ConfluentSchemaRegistryClient, OpenAPIJsonSchema}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.{ConfluentAvroMessageFormatter, ConfluentAvroMessageReader}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization._
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload.JsonPayloadKafkaSerializer
import pl.touk.nussknacker.engine.avro.sink.AvroSinkValueParameter
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.json.{JsonSinkValueParameter, SwaggerBasedJsonSchemaTypeDefinitionExtractor}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.engine.util.output.OutputValidatorError
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.SinkValueParameter

import java.nio.charset.StandardCharsets

sealed trait ParsedSchemaSupport[+S <: ParsedSchema] extends UniversalSchemaSupport {
  protected implicit class RichParsedSchema(p: ParsedSchema){
    def cast(): S = p.asInstanceOf[S]
  }
}

object AvroSchemaSupport extends ParsedSchemaSupport[AvroSchema] {
  override val payloadDeserializer: UniversalSchemaPayloadDeserializer = ConfluentAvroPayloadDeserializer.default

  override def serializer[T](schema: ParsedSchema, client: ConfluentSchemaRegistryClient, kafkaConfig: KafkaConfig, isKey: Boolean): Serializer[T] =
    ConfluentKafkaAvroSerializer(kafkaConfig, client, Some(schema.cast()), isKey = isKey).asInstanceOf[Serializer[T]]

  override def messageFormatter(client: SchemaRegistryClient): Any => Json =
     (obj: Any) => new ConfluentAvroMessageFormatter(client).asJson(obj)

  override def messageReader(schema: ParsedSchema, client: SchemaRegistryClient): (Json, String) => Array[Byte] =
    (jsonObj, subject) => new ConfluentAvroMessageReader(client).readJson(jsonObj, schema.cast().rawSchema(), subject)

  override def typeDefinition(schema: ParsedSchema): TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.cast().rawSchema())

  override def extractSinkValueParameter(schema: ParsedSchema)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] = AvroSinkValueParameter(schema.cast().rawSchema())

  override def sinkValueEncoder(schema: ParsedSchema, validationMode: ValidationMode): Any => AnyRef =
    (value: Any) => BestEffortAvroEncoder(validationMode).encodeOrError(value, schema.cast().rawSchema())

  override def validateRawOutput(schema: ParsedSchema, t: TypingResult, mode: ValidationMode)(implicit nodeId: NodeId): ValidatedNel[OutputValidatorError, Unit] =
    new AvroSchemaOutputValidator(mode).validateTypingResultToSchema(t, schema.cast().rawSchema())
}


object JsonSchemaSupport extends ParsedSchemaSupport[OpenAPIJsonSchema] {
  override val payloadDeserializer: UniversalSchemaPayloadDeserializer = ConfluentJsonSchemaPayloadDeserializer

  override def serializer[T](schema: ParsedSchema, c: ConfluentSchemaRegistryClient, k: KafkaConfig, isKey: Boolean): Serializer[T] = (topic: String, data: T) => data match {
    case j: Json => j.noSpaces.getBytes()
    case _ => throw new SerializationException(s"Expecting json but got: $data")
  }

  override def messageFormatter(c: SchemaRegistryClient): Any => Json =
    (obj: Any) => BestEffortJsonEncoder(failOnUnkown = false, classLoader = getClass.getClassLoader).encode(obj)

  override def messageReader(schema: ParsedSchema, c: SchemaRegistryClient): (Json, String) => Array[Byte] = (jsonObj, _) => jsonObj match {
    // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
    case j if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
    case other => other.noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  override def typeDefinition(schema: ParsedSchema): TypingResult = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema.cast().swaggerJsonSchema).typingResult

  override def extractSinkValueParameter(schema: ParsedSchema)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] =
    JsonSinkValueParameter(schema.cast().rawSchema(), defaultParamName = SinkValueParamName)

  override def sinkValueEncoder(schema: ParsedSchema, mode: ValidationMode): Any => AnyRef =
    (value: Any) => new BestEffortJsonSchemaEncoder(ValidationMode.lax) //todo: pass real validation mode, when BestEffortJsonSchemaEncoder supports it
    .encodeOrError(value, schema.cast().rawSchema())

  override def validateRawOutput(schema: ParsedSchema, t: TypingResult, mode: ValidationMode)(implicit nodeId: NodeId): ValidatedNel[OutputValidatorError, Unit] =
    new JsonSchemaOutputValidator(mode).validateTypingResultToSchema(t, schema.cast().rawSchema())
}


object AvroSchemaWithJsonPayloadSupport extends ParsedSchemaSupport[AvroSchemaWithJsonPayload] {

  override val payloadDeserializer: UniversalSchemaPayloadDeserializer = ConfluentJsonPayloadDeserializer

  override def serializer[T](schema: ParsedSchema, client: ConfluentSchemaRegistryClient, kafkaConfig: KafkaConfig, isKey: Boolean): Serializer[T] =
    new JsonPayloadKafkaSerializer(kafkaConfig, client, new DefaultAvroSchemaEvolution, Some(schema.cast().avroSchema), isKey = isKey).asInstanceOf[Serializer[T]]

  override def messageFormatter(c: SchemaRegistryClient): Any => Json =
    (obj: Any) => BestEffortJsonEncoder(failOnUnkown = false, classLoader = getClass.getClassLoader).encode(obj)

  override def messageReader(schema: ParsedSchema, c: SchemaRegistryClient): (Json, String) => Array[Byte] =
    (jsonObj, _) => jsonObj match {
      // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
      case j if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
      case other => other.noSpaces.getBytes(StandardCharsets.UTF_8)
    }

  override def typeDefinition(schema: ParsedSchema): TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.cast().rawSchema())

  override def extractSinkValueParameter(schema: ParsedSchema)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] =  AvroSinkValueParameter(schema.cast().rawSchema())

  override def sinkValueEncoder(schema: ParsedSchema, mode: ValidationMode): Any => AnyRef =
    (value: Any) => BestEffortAvroEncoder(mode).encodeOrError(value, schema.cast().rawSchema())

  override def validateRawOutput(schema: ParsedSchema, t: TypingResult, mode: ValidationMode)(implicit nodeId: NodeId): ValidatedNel[OutputValidatorError, Unit] =
    new AvroSchemaOutputValidator(mode).validateTypingResultToSchema(t, schema.cast().rawSchema())
}
