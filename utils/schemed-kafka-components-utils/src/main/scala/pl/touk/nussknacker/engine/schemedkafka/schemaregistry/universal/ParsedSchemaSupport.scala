package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import cats.data.{Validated, ValidatedNel}
import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.json.JsonSchemaBasedParameter
import pl.touk.nussknacker.engine.json.encode.{JsonSchemaOutputValidator, ToJsonSchemaBasedEncoder}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.sinkValueParamName
import pl.touk.nussknacker.engine.schemedkafka.encode._
import pl.touk.nussknacker.engine.schemedkafka.schema.{AvroSchemaBasedParameter, DefaultAvroSchemaEvolution}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.AzureSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.serialization.AzureAvroSerializerFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{
  ConfluentSchemaRegistryClient,
  OpenAPIJsonSchema
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.jsonpayload.ConfluentJsonPayloadKafkaSerializer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.formatter.AvroMessageReader
import pl.touk.nussknacker.engine.schemedkafka.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.util.parameters.{SchemaBasedParameter, SingleSchemaBasedParameter}

sealed trait ParsedSchemaSupport[+S <: ParsedSchema] extends UniversalSchemaSupport {

  protected implicit class RichParsedSchema(p: ParsedSchema) {
    def cast(): S = p.asInstanceOf[S]
  }

}

class AvroSchemaSupport(kafkaConfig: KafkaConfig) extends ParsedSchemaSupport[AvroSchema] {

  override val payloadDeserializer: UniversalSchemaPayloadDeserializer = {
    if (kafkaConfig.avroAsJsonSerialization.contains(true)) {
      JsonPayloadDeserializer
    } else {
      AvroPayloadDeserializer(kafkaConfig)
    }
  }

  override def serializer(
      schemaOpt: Option[ParsedSchema],
      client: Option[SchemaRegistryClient],
      isKey: Boolean
  ): Serializer[Any] = {
    client match {
      case Some(confluentClient: ConfluentSchemaRegistryClient) if kafkaConfig.avroAsJsonSerialization.contains(true) =>
        new ConfluentJsonPayloadKafkaSerializer(
          kafkaConfig,
          confluentClient,
          new DefaultAvroSchemaEvolution,
          schemaOpt.map(_.cast()),
          isKey = isKey
        )
      case Some(confluentClient: ConfluentSchemaRegistryClient) =>
        ConfluentKafkaAvroSerializer(kafkaConfig, confluentClient, schemaOpt.map(_.cast()), isKey = isKey)
      case Some(azureClient: AzureSchemaRegistryClient) =>
        AzureAvroSerializerFactory.createSerializer(azureClient, kafkaConfig, schemaOpt.map(_.cast()), isKey)
      // TODO_PAWEL no wlasnie, jak nie ma to nie ma, nie ma znaczenia czy nie ma azure czy
      case None =>
      case _ =>
        throw new IllegalArgumentException(
          s"Not supported schema registry client: ${client.getClass}. " +
            s"Avro serialization is currently supported only for Confluent schema registry implementation"
        )
    }

  }

  override def typeDefinition(schema: ParsedSchema): TypingResult =
    AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.cast().rawSchema())

  override def extractParameter(
      schema: ParsedSchema,
      rawMode: Boolean,
      validationMode: ValidationMode,
      rawParameter: Parameter,
      restrictedParamNames: Set[ParameterName]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] = {
    if (rawMode) {
      Validated.Valid(
        SingleSchemaBasedParameter(
          rawParameter,
          new AvroSchemaOutputValidator(validationMode).validate(_, schema.cast().rawSchema())
        )
      )
    } else {
      AvroSchemaBasedParameter(schema.cast().rawSchema(), restrictedParamNames)
    }
  }

  override def formValueEncoder(schema: ParsedSchema, validationMode: ValidationMode): Any => AnyRef = {
    val encoder = ToAvroSchemaBasedEncoder(validationMode)
    (value: Any) => encoder.encodeOrError(value, schema.cast().rawSchema())
  }

  override def recordFormatterSupport(schemaRegistryClient: SchemaRegistryClient): RecordFormatterSupport = {
    if (kafkaConfig.avroAsJsonSerialization.contains(true)) {
      JsonPayloadRecordFormatterSupport
    } else {
      // We pass None to schema, because message readers should not do schema evolution.
      // It is done this way because we want to keep messages in the original format as they were serialized on Kafka
      val createSerializer    = serializer(None, schemaRegistryClient, _)
      val avroKeySerializer   = createSerializer(true)
      val avroValueSerializer = createSerializer(false)
      new AvroPayloadRecordFormatterSupport(
        new AvroMessageReader(avroKeySerializer),
        new AvroMessageReader(avroValueSerializer)
      )
    }
  }

}

object JsonSchemaSupport extends ParsedSchemaSupport[OpenAPIJsonSchema] {
  override val payloadDeserializer: UniversalSchemaPayloadDeserializer = JsonSchemaPayloadDeserializer

  override def serializer(schemaOpt: Option[ParsedSchema], c: SchemaRegistryClient, isKey: Boolean): Serializer[Any] =
    (topic: String, data: Any) =>
      data match {
        case j: Json => j.noSpaces.getBytes()
        case _       => throw new SerializationException(s"Expecting json but got: $data")
      }

  override def typeDefinition(schema: ParsedSchema): TypingResult = schema.cast().returnType

  override def extractParameter(
      schema: ParsedSchema,
      rawMode: Boolean,
      validationMode: ValidationMode,
      rawParameter: Parameter,
      restrictedParamNames: Set[ParameterName]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] = {
    if (rawMode) {
      Validated.Valid(
        SingleSchemaBasedParameter(
          rawParameter,
          new JsonSchemaOutputValidator(validationMode).validate(_, schema.cast().rawSchema())
        )
      )
    } else {
      // in editor mode we use lax validation mode, to be backward compatible
      JsonSchemaBasedParameter(schema.cast().rawSchema(), defaultParamName = sinkValueParamName, ValidationMode.lax)
    }
  }

  override def formValueEncoder(schema: ParsedSchema, mode: ValidationMode): Any => AnyRef = {
    val encoder   = new ToJsonSchemaBasedEncoder(mode)
    val rawSchema = schema.cast().rawSchema()
    (value: Any) => encoder.encodeOrError(value, rawSchema)
  }

  override def recordFormatterSupport(schemaRegistryClient: SchemaRegistryClient): RecordFormatterSupport =
    JsonPayloadRecordFormatterSupport
}
