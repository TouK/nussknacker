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
import pl.touk.nussknacker.engine.avro.encode.{AvroSchemaOutputValidator, BestEffortAvroEncoder, BestEffortJsonSchemaEncoder, JsonSchemaOutputValidator, ValidationMode}
import pl.touk.nussknacker.engine.avro.schema.DefaultAvroSchemaEvolution
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{AvroSchemaWithJsonPayload, ConfluentSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.{ConfluentAvroMessageFormatter, ConfluentAvroMessageReader}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentAvroPayloadDeserializer, ConfluentJsonPayloadDeserializer, ConfluentJsonSchemaPayloadDeserializer, ConfluentKafkaAvroSerializer, UniversalSchemaPayloadDeserializer}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload.JsonPayloadKafkaSerializer
import pl.touk.nussknacker.engine.avro.sink.AvroSinkValueParameter
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.json.{JsonSchemaTypeDefinitionExtractor, JsonSinkValueParameter}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.engine.util.output.OutputValidatorError
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.SinkValueParameter

import java.nio.charset.StandardCharsets

sealed trait ParsedSchemaSupport[+S <: ParsedSchema] extends UniversalSchemaSupport {
  val schema: S
}

case class AvroSchemaSupport(schema: AvroSchema) extends ParsedSchemaSupport[AvroSchema] {
  override val payloadDeserializer: UniversalSchemaPayloadDeserializer = ConfluentAvroPayloadDeserializer.default

  override def serializerFactory[T]: (ConfluentSchemaRegistryClient, KafkaConfig, Boolean) => Serializer[T] =
    (client, kafkaConfig, isKey) => ConfluentKafkaAvroSerializer(kafkaConfig, client, Some(schema), isKey = isKey).asInstanceOf[Serializer[T]]

  override def messageFormatterFactory: SchemaRegistryClient => Any => Json =
    (client: SchemaRegistryClient) => (obj: Any) => new ConfluentAvroMessageFormatter(client).asJson(obj)

  override def messageReaderFactory: SchemaRegistryClient => (Json, String) => Array[Byte] =
    client => (jsonObj, subject) => new ConfluentAvroMessageReader(client).readJson(jsonObj, schema.rawSchema(), subject)


  override def typeDefinition: TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.rawSchema())

  override def extractSinkValueParameter(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] = AvroSinkValueParameter(schema.rawSchema())

  override def sinkValueEncoderFactory: ValidationMode => Any => AnyRef = validationMode => (value: Any) => BestEffortAvroEncoder(validationMode).encodeOrError(value, schema.rawSchema())

  override def rawOutputValidatorFactory(implicit nodeId: NodeId): (TypingResult, ValidationMode) => ValidatedNel[OutputValidatorError, Unit] =
    (t, mode: ValidationMode) => new AvroSchemaOutputValidator(mode).validateTypingResultToSchema(t, schema.rawSchema())
}



case class JsonSchemaSupport(schema: JsonSchema) extends ParsedSchemaSupport[JsonSchema] {
  override val payloadDeserializer: UniversalSchemaPayloadDeserializer = ConfluentJsonSchemaPayloadDeserializer

  override def serializerFactory[T]: (ConfluentSchemaRegistryClient, KafkaConfig, Boolean) => Serializer[T] = (_, _, _) =>
    (topic: String, data: T) => data match {
      case j: Json => j.noSpaces.getBytes()
      case _ => throw new SerializationException(s"Expecting json but got: $data")
    }

  override def messageFormatterFactory: SchemaRegistryClient => Any => Json =
    _ => (obj: Any) => BestEffortJsonEncoder(failOnUnkown = false, classLoader = getClass.getClassLoader).encode(obj)

  override def messageReaderFactory: SchemaRegistryClient => (Json, String) => Array[Byte] =
    _ => (jsonObj, _) => jsonObj match {
      // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
      case j if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
      case other => other.noSpaces.getBytes(StandardCharsets.UTF_8)
    }

  override def typeDefinition: TypingResult = JsonSchemaTypeDefinitionExtractor.typeDefinition(schema.rawSchema())

  override def extractSinkValueParameter(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] = JsonSinkValueParameter(schema.rawSchema(), defaultParamName = SinkValueParamName)

  override def sinkValueEncoderFactory: ValidationMode => Any => AnyRef =
    validationMode => (value: Any) => new BestEffortJsonSchemaEncoder(ValidationMode.lax) //todo: pass real validation mode, when BestEffortJsonSchemaEncoder supports it
      .encodeOrError(value, schema.rawSchema())

  override def rawOutputValidatorFactory(implicit nodeId: NodeId): (TypingResult, ValidationMode) => ValidatedNel[OutputValidatorError, Unit] =
    (t, mode: ValidationMode) => new JsonSchemaOutputValidator(mode).validateTypingResultToSchema(t, schema.rawSchema())

}



case class AvroSchemaWithJsonPayloadSupport(schema: AvroSchemaWithJsonPayload) extends ParsedSchemaSupport[AvroSchemaWithJsonPayload] {
  override val payloadDeserializer: UniversalSchemaPayloadDeserializer = ConfluentJsonPayloadDeserializer

  override def serializerFactory[T]: (ConfluentSchemaRegistryClient, KafkaConfig, Boolean) => Serializer[T] =
    (client, kafkaConfig, isKey) => new JsonPayloadKafkaSerializer(kafkaConfig, client, new DefaultAvroSchemaEvolution, Some(schema.avroSchema), isKey = isKey).asInstanceOf[Serializer[T]]

  override def messageFormatterFactory: SchemaRegistryClient => Any => Json =
    _ => (obj: Any) => BestEffortJsonEncoder(failOnUnkown = false, classLoader = getClass.getClassLoader).encode(obj)

  override def messageReaderFactory: SchemaRegistryClient => (Json, String) => Array[Byte] =
    _ => (jsonObj, _) => jsonObj match {
      // we handle strings this way because we want to keep result value compact and JString is formatted in quotes
      case j if j.isString => j.asString.get.getBytes(StandardCharsets.UTF_8)
      case other => other.noSpaces.getBytes(StandardCharsets.UTF_8)
    }

  override def typeDefinition: TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.rawSchema())

  override def extractSinkValueParameter(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] = AvroSinkValueParameter(schema.rawSchema())

  override def sinkValueEncoderFactory: ValidationMode => Any => AnyRef = validationMode => (value: Any) => BestEffortAvroEncoder(validationMode).encodeOrError(value, schema.rawSchema())

  override def rawOutputValidatorFactory(implicit nodeId: NodeId): (TypingResult, ValidationMode) => ValidatedNel[OutputValidatorError, Unit] =
    (t, mode: ValidationMode) => new AvroSchemaOutputValidator(mode).validateTypingResultToSchema(t, schema.rawSchema())
}
