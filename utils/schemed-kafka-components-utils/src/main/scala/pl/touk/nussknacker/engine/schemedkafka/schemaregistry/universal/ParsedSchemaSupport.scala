package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import cats.data.{Validated, ValidatedNel}
import cats.data.Validated.Valid
import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Serializer
import org.everit.json.schema.EmptySchema
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.{
  JsonParameterEditor,
  JsonTemplateParameterEditor,
  Parameter,
  SpelParameterEditor
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.json.{JsonSchemaBasedParameter, JsonTemplateFromJsonSchemaDeterminer}
import pl.touk.nussknacker.engine.json.encode.{JsonSchemaOutputValidator, ToJsonSchemaBasedEncoder}
import pl.touk.nussknacker.engine.kafka.KafkaComponentsConfig
import pl.touk.nussknacker.engine.kafka.serialization.CharSequenceSerializer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{inputParamName, sinkValueParamName}
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
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.GenericRecordSchemaIdSerializationSupport
import pl.touk.nussknacker.engine.schemedkafka.typed.{
  AvroSchemaTypeDefinitionExtractor,
  AvroSchemaTypeDefinitionExtractorWithUnderlyingMap
}
import pl.touk.nussknacker.engine.util.parameters.{
  SchemaBasedParameter,
  SingleSchemaBasedParameter,
  TypingResultValidator
}

sealed trait ParsedSchemaSupport[+S <: ParsedSchema] extends UniversalSchemaSupport {

  protected implicit class RichParsedSchema(p: ParsedSchema) {
    def cast(): S = p.asInstanceOf[S]
  }

}

class AvroSchemaSupport(kafkaComponentsConfig: KafkaComponentsConfig) extends ParsedSchemaSupport[AvroSchema] {

  private val genericRecordSchemaIdSerializationSupport = GenericRecordSchemaIdSerializationSupport(
    kafkaComponentsConfig
  )

  override val payloadDeserializer: UniversalSchemaPayloadDeserializer = {
    if (kafkaComponentsConfig.avroAsJsonSerialization.contains(true)) {
      JsonPayloadDeserializer
    } else {
      new AvroPayloadDeserializer(genericRecordSchemaIdSerializationSupport)
    }
  }

  override def serializer(
      schemaOpt: Option[ParsedSchema],
      client: SchemaRegistryClient,
      isKey: Boolean
  ): Serializer[Any] = {
    client match {
      case confluentClient: ConfluentSchemaRegistryClient
          if kafkaComponentsConfig.avroAsJsonSerialization.contains(true) =>
        new ConfluentJsonPayloadKafkaSerializer(
          kafkaComponentsConfig,
          confluentClient,
          new DefaultAvroSchemaEvolution,
          schemaOpt.map(_.cast()),
          isKey = isKey
        )
      case confluentClient: ConfluentSchemaRegistryClient =>
        ConfluentKafkaAvroSerializer(kafkaComponentsConfig, confluentClient, schemaOpt.map(_.cast()), isKey = isKey)
      case azureClient: AzureSchemaRegistryClient =>
        AzureAvroSerializerFactory.createSerializer(azureClient, kafkaComponentsConfig, schemaOpt.map(_.cast()), isKey)
      case _ =>
        throw new IllegalArgumentException(
          s"Not supported schema registry client: ${client.getClass}. " +
            s"Avro serialization is currently supported only for Confluent schema registry implementation"
        )
    }

  }

  override def typeDefinition(schema: ParsedSchema): TypingResult = {
    if (kafkaComponentsConfig.avroAsJsonSerialization.contains(true)) {
      AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.cast().rawSchema())
    } else {
      genericRecordSchemaIdSerializationSupport.wrapRecordTypeIfNeeded(
        AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.cast().rawSchema())
      )
    }
  }

  override def formValueEncoder(schema: ParsedSchema, validationMode: ValidationMode): Any => AnyRef = {
    val encoder = ToAvroSchemaBasedEncoder(validationMode)
    (value: Any) => encoder.encodeOrError(value, schema.cast().rawSchema())
  }

  override def recordFormatterSupport(schemaRegistryClient: SchemaRegistryClient): RecordFormatterSupport = {
    if (kafkaComponentsConfig.avroAsJsonSerialization.contains(true)) {
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

  override def extractSingleParameterForSink(
      schema: ParsedSchema,
      validationMode: ValidationMode,
      rawParameter: Parameter
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SingleSchemaBasedParameter] = {
    Validated.Valid(
      SingleSchemaBasedParameter(
        rawParameter.copy(
          defaultValue = Some(defaultJsonTemplateFor(schema)),
          editors = List(JsonTemplateParameterEditor, SpelParameterEditor)
        ),
        new AvroSchemaOutputValidator(validationMode).validate(_, schema.cast().rawSchema())
      )
    )
  }

  override def extractDynamicParametersForSink(schema: ParsedSchema, restrictedParamNames: Set[ParameterName])(
      implicit nodeId: NodeId
  ): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] = {
    AvroSchemaBasedParameter(
      schema.cast().rawSchema(),
      restrictedParamNames,
      // We need custom AvroSchemaTypeDefinitionExtractor here as otherwise SpelExpressionValidator rises an errors
      // when rawmode is off and sink schema contains nested records
      AvroSchemaTypeDefinitionExtractorWithUnderlyingMap
    )
  }

  override def extractParameterForTests(schema: ParsedSchema)(
      implicit nodeId: NodeId
  ): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] =
    extractDynamicParametersForSink(
      schema,
      restrictedParamNames = Set.empty
    )

  private def defaultJsonTemplateFor(parsedSchema: ParsedSchema): Expression = {
    val schema = parsedSchema.cast().rawSchema()
    JsonTemplateFromAvroSchemaDeterminer.jsonTemplateFor(schema)
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

  override def formValueEncoder(schema: ParsedSchema, mode: ValidationMode): Any => AnyRef = {
    val encoder   = new ToJsonSchemaBasedEncoder(mode)
    val rawSchema = schema.cast().rawSchema()
    (value: Any) => encoder.encodeOrError(value, rawSchema)
  }

  override def recordFormatterSupport(schemaRegistryClient: SchemaRegistryClient): RecordFormatterSupport =
    JsonPayloadRecordFormatterSupport

  override def extractSingleParameterForSink(
      schema: ParsedSchema,
      validationMode: ValidationMode,
      rawParameter: Parameter
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SingleSchemaBasedParameter] = {
    Valid(
      SingleSchemaBasedParameter(
        rawParameter.copy(
          defaultValue = Some(defaultJsonTemplateFor(schema)),
          editors = List(
            JsonTemplateParameterEditor,
            SpelParameterEditor
          )
        ),
        new JsonSchemaOutputValidator(validationMode).validate(_, schema.cast().rawSchema())
      )
    )
  }

  override def extractDynamicParametersForSink(
      schema: ParsedSchema,
      restrictedParamNames: Set[ParameterName]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] = {
    // in editor mode we use lax validation mode, to be backward compatible
    JsonSchemaBasedParameter(schema.cast().rawSchema(), defaultParamName = sinkValueParamName, ValidationMode.lax)
      .withJsonEditors()
  }

  override def extractParameterForTests(schema: ParsedSchema)(
      implicit nodeId: NodeId
  ): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] =
    extractDynamicParametersForSink(
      schema,
      restrictedParamNames = Set.empty
    )

  private def defaultJsonTemplateFor(parsedSchema: ParsedSchema): Expression = {
    val schema = parsedSchema.cast().rawSchema()
    JsonTemplateFromJsonSchemaDeterminer.jsonTemplateFor(schema)
  }

  implicit class WithJsonEditorsExtension(
      parameter: ValidatedNel[ProcessCompilationError, SchemaBasedParameter]
  ) {

    def withJsonEditors(): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] = parameter
      .map {
        case s @ SingleSchemaBasedParameter(value, _) if value.editors.isEmpty =>
          s.copy(value =
            s.value.copy(
              editors = List(
                JsonTemplateParameterEditor,
                SpelParameterEditor
              )
            )
          )
        case other => other
      }

  }

}

object NoSchemaJsonSupport extends UniversalSchemaSupport {

  private final val jsonSupport = JsonSchemaSupport

  override val payloadDeserializer: UniversalSchemaPayloadDeserializer =
    new JsonTypingResultPayloadDeserializer(Unknown)

  override def serializer(schemaOpt: Option[ParsedSchema], c: SchemaRegistryClient, isKey: Boolean): Serializer[Any] =
    jsonSupport.serializer(schemaOpt, c, isKey)

  override def typeDefinition(schema: ParsedSchema): TypingResult = jsonSupport.typeDefinition(schema)

  override def formValueEncoder(schema: ParsedSchema, mode: ValidationMode): Any => AnyRef =
    jsonSupport.formValueEncoder(schema, mode)

  override def recordFormatterSupport(schemaRegistryClient: SchemaRegistryClient): RecordFormatterSupport =
    jsonSupport.recordFormatterSupport(schemaRegistryClient)

  override def extractSingleParameterForSink(
      schema: ParsedSchema,
      validationMode: ValidationMode,
      rawParameter: Parameter
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SingleSchemaBasedParameter] = {
    jsonSupport.extractSingleParameterForSink(schema, validationMode, rawParameter)
  }

  override def extractDynamicParametersForSink(
      schema: ParsedSchema,
      restrictedParamNames: Set[ParameterName]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] =
    jsonSupport.extractDynamicParametersForSink(
      schema,
      restrictedParamNames
    )

  override def extractParameterForTests(schema: ParsedSchema)(implicit nodeId: NodeId): Valid[SchemaBasedParameter] = {
    val parameter =
      Parameter(inputParamName, Typed.json).copy(isLazyParameter = true, editors = List(JsonParameterEditor))
    Valid(
      SingleSchemaBasedParameter(
        parameter,
        new JsonSchemaOutputValidator(ValidationMode.lax).validate(_, EmptySchema.INSTANCE, None)
      )
    )
  }

}

object NoSchemaPlainSupport extends ParsedSchemaSupport[OpenAPIJsonSchema] {

  override def payloadDeserializer: UniversalSchemaPayloadDeserializer = PlainTextPayloadDeserializer

  override def serializer(schemaOpt: Option[ParsedSchema], c: SchemaRegistryClient, isKey: Boolean): Serializer[Any] =
    (new CharSequenceSerializer).asInstanceOf[Serializer[Any]]

  override def typeDefinition(schema: ParsedSchema): TypingResult = Typed[String]

  override def formValueEncoder(schema: ParsedSchema, mode: ValidationMode): Any => AnyRef = a => a.asInstanceOf[AnyRef]

  override def extractSingleParameterForSink(
      schema: ParsedSchema,
      validationMode: ValidationMode,
      rawParameter: Parameter
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SingleSchemaBasedParameter] = Valid(
    SingleSchemaBasedParameter(
      rawParameter.copy(
        typ = Typed[String]
      ),
      TypingResultValidator.emptyValidator
    )
  )

  // Below methods shouldn't be applicable
  override def recordFormatterSupport(schemaRegistryClient: SchemaRegistryClient): RecordFormatterSupport =
    JsonSchemaSupport.recordFormatterSupport(schemaRegistryClient)

  override def extractDynamicParametersForSink(schema: ParsedSchema, restrictedParamNames: Set[ParameterName])(
      implicit nodeId: NodeId
  ): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] =
    JsonSchemaSupport.extractDynamicParametersForSink(schema, restrictedParamNames)

  override def extractParameterForTests(schema: ParsedSchema)(
      implicit nodeId: NodeId
  ): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] =
    JsonSchemaSupport.extractParameterForTests(schema)

}
