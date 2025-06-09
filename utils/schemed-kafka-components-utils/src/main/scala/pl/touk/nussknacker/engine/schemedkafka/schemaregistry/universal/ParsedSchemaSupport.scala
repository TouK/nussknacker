package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import cats.data.{Validated, ValidatedNel}
import cats.data.Validated.Valid
import cats.implicits.catsSyntaxOptionId
import io.circe.{Encoder, Json}
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.{JsonProperties, LogicalTypes, Schema}
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Serializer
import org.everit.json.schema.{
  ArraySchema,
  BooleanSchema,
  CombinedSchema,
  EmptySchema,
  EnumSchema,
  NullSchema,
  NumberSchema,
  ObjectSchema,
  ReferenceSchema,
  Schema => JsonSchema,
  StringSchema
}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.{
  JsonParameterEditor,
  JsonTemplateParameterEditor,
  Parameter,
  SpelParameterEditor
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.json.JsonSchemaBasedParameter
import pl.touk.nussknacker.engine.json.encode.{JsonSchemaOutputValidator, ToJsonSchemaBasedEncoder}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
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
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaSupport.ParameterExtractionMode
import pl.touk.nussknacker.engine.schemedkafka.typed.{
  AvroSchemaTypeDefinitionExtractor,
  AvroSchemaTypeDefinitionExtractorWithUnderlyingMap
}
import pl.touk.nussknacker.engine.util.json.JsonSchemaUtils.jsonToCirce
import pl.touk.nussknacker.engine.util.parameters.{SchemaBasedParameter, SingleSchemaBasedParameter}

import scala.collection.compat.immutable.LazyList
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

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
      client: SchemaRegistryClient,
      isKey: Boolean
  ): Serializer[Any] = {
    client match {
      case confluentClient: ConfluentSchemaRegistryClient if kafkaConfig.avroAsJsonSerialization.contains(true) =>
        new ConfluentJsonPayloadKafkaSerializer(
          kafkaConfig,
          confluentClient,
          new DefaultAvroSchemaEvolution,
          schemaOpt.map(_.cast()),
          isKey = isKey
        )
      case confluentClient: ConfluentSchemaRegistryClient =>
        ConfluentKafkaAvroSerializer(kafkaConfig, confluentClient, schemaOpt.map(_.cast()), isKey = isKey)
      case azureClient: AzureSchemaRegistryClient =>
        AzureAvroSerializerFactory.createSerializer(azureClient, kafkaConfig, schemaOpt.map(_.cast()), isKey)
      case _ =>
        throw new IllegalArgumentException(
          s"Not supported schema registry client: ${client.getClass}. " +
            s"Avro serialization is currently supported only for Confluent schema registry implementation"
        )
    }

  }

  override def typeDefinition(schema: ParsedSchema): TypingResult =
    AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.cast().rawSchema())

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

  override def extractSingleParameterForSink(
      schema: ParsedSchema,
      parameterExtractionMode: ParameterExtractionMode,
      validationMode: ValidationMode,
      rawParameter: Parameter
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SingleSchemaBasedParameter] = {
    parameterExtractionMode match {
      case ParameterExtractionMode.RawParameter =>
        Validated.Valid(
          SingleSchemaBasedParameter(
            rawParameter,
            new AvroSchemaOutputValidator(validationMode).validate(_, schema.cast().rawSchema())
          )
        )
      case ParameterExtractionMode.RawParameterTemplate =>
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
  }

  override def extractParametersForSink(schema: ParsedSchema, restrictedParamNames: Set[ParameterName])(
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
    extractParametersForSink(
      schema,
      restrictedParamNames = Set.empty
    )

  private def defaultJsonTemplateFor(parsedSchema: ParsedSchema): Expression = {
    val schema         = parsedSchema.cast().rawSchema()
    val expressionJson = defaultJsonFor(schema)
    Expression.jsonTemplate(expressionJson.getOrElse(Json.obj()).spaces2)
  }

  private def defaultJsonFor(schema: Schema): Option[Json] = {
    schema.getType match {
      case Schema.Type.RECORD =>
        val recordFields = schema.getFields.asScala.toList
        val jsonFields = recordFields.map { field =>
          field
            .name() -> defaultJsonForRecordField(field)
            .orElse(jsonBasedOnSchema(field.schema()))
            .getOrElse(Json.Null)
        }
        Some(Json.fromFields(jsonFields.sortBy(_._1)))
      case other => // only record fields can have defaults
        jsonBasedOnSchema(schema)
    }
  }

  private def defaultJsonForRecordField(fieldSchema: Schema.Field): Option[Json] = {
    val schema = fieldSchema.schema()
    schema.getType match {
      case Schema.Type.RECORD =>
        val fields = schema.getFields.asScala.flatMap { field =>
          defaultJsonFor(field.schema()).map(fieldJson => field.name() -> fieldJson)
        }.toMap
        Json.obj(fields.toSeq: _*).some
      case Schema.Type.ENUM =>
        withDefaultValueAs[String](fieldSchema)
      case Schema.Type.ARRAY =>
        None
      case Schema.Type.MAP =>
        Json.obj().some
      case Schema.Type.UNION =>
        // For unions Avro supports default to be the type of the first type in the union. See: https://issues.apache.org/jira/browse/AVRO-1118
        schema.getTypes.asScala.headOption match {
          case Some(firstUnionSchema) => defaultJsonFor(firstUnionSchema)
          case None                   => Json.Null.some
        }
      case Schema.Type.STRING => withDefaultValueAs[String](fieldSchema)
      case Schema.Type.BYTES  => None
      case Schema.Type.INT if schema.getLogicalType == LogicalTypes.date() =>
        None
      case Schema.Type.INT if schema.getLogicalType == LogicalTypes.timeMillis() =>
        None
      case Schema.Type.INT => withDefaultValueAs[Integer](fieldSchema)
      case Schema.Type.LONG =>
        withDefaultValueAs[java.lang.Long](fieldSchema)
      case Schema.Type.FLOAT =>
        withDefaultValueAs[java.lang.Float](fieldSchema)
      case Schema.Type.DOUBLE =>
        withDefaultValueAs[java.lang.Double](fieldSchema)
      case Schema.Type.BOOLEAN =>
        withDefaultValueAs[java.lang.Boolean](fieldSchema)
      case Schema.Type.NULL => Some(Json.Null)
      case Schema.Type.FIXED =>
        Option(fieldSchema.defaultVal()) match {
          case Some(array: Array[Byte]) => Some(Json.fromString(fixed(array)))
          case _                        => None
        }
    }
  }

  private def withDefaultValueAs[T <: AnyRef: ClassTag: Encoder](fieldSchema: Schema.Field): Option[Json] =
    Option(fieldSchema.defaultVal()) match {
      case Some(JsonProperties.NULL_VALUE) => Some(Json.Null)
      case Some(value: T)                  => Some(Encoder[T].apply(value))
      case Some(_) | None                  => None
    }

  private def jsonBasedOnSchema(schema: Schema): Option[Json] = {
    schema.getType match {
      case Schema.Type.RECORD =>
        val fields = schema.getFields.asScala.flatMap { field =>
          jsonBasedOnSchema(field.schema()).map(defaultValue => field.name() -> defaultValue)
        }
        Json.fromFields(fields.sortBy(_._1)).some
      case Schema.Type.ENUM =>
        Option(schema.getEnumDefault).orElse(schema.getEnumSymbols.asScala.headOption).map(Json.fromString)
      case Schema.Type.ARRAY =>
        jsonBasedOnSchema(schema.getElementType).map(Json.arr(_)).getOrElse(Json.arr()).some
      case Schema.Type.MAP =>
        Json.obj().some
      case Schema.Type.UNION =>
        schema.getTypes.asScala.headOption.flatMap(jsonBasedOnSchema)
      case Schema.Type.STRING  => Json.fromString("#{ '' }").some
      case Schema.Type.BYTES   => Json.fromString("").some
      case Schema.Type.INT     => Some(Json.fromInt(0))
      case Schema.Type.LONG    => Some(Json.fromLong(0L))
      case Schema.Type.BOOLEAN => Some(Json.fromBoolean(true))
      case Schema.Type.FLOAT   => Some(Json.fromFloatOrNull(0.0f))
      case Schema.Type.DOUBLE  => Some(Json.fromDoubleOrNull(0.0))
      case Schema.Type.NULL    => Some(Json.Null)
      case Schema.Type.FIXED   => Json.fromString(fixed(Array.fill[Byte](schema.getFixedSize)(0))).some
    }
  }

  private def fixed(array: Array[Byte]): String = {
    new String(array, "ISO-8859-1")
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
      parameterExtractionMode: ParameterExtractionMode,
      validationMode: ValidationMode,
      rawParameter: Parameter
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SingleSchemaBasedParameter] = {
    parameterExtractionMode match {
      case ParameterExtractionMode.RawParameter =>
        Valid(
          SingleSchemaBasedParameter(
            rawParameter.copy(
              editors = List(
                JsonTemplateParameterEditor,
                SpelParameterEditor
              )
            ),
            new JsonSchemaOutputValidator(validationMode).validate(_, schema.cast().rawSchema())
          )
        )
      case ParameterExtractionMode.RawParameterTemplate =>
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
  }

  override def extractParametersForSink(
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
    extractParametersForSink(
      schema,
      restrictedParamNames = Set.empty
    )

  private def defaultJsonTemplateFor(parsedSchema: ParsedSchema): Expression = {
    val schema = parsedSchema.cast().rawSchema()
    val json   = defaultJsonFor(schema).getOrElse(Json.obj())
    Expression.jsonTemplate(json.spaces2)
  }

  private def defaultJsonFor(schema: JsonSchema): Option[Json] = schema match {
    case schema: JsonSchema if schema.hasDefaultValue =>
      val defaultValue = schema.getDefaultValue
      Some(jsonToCirce(defaultValue))
    case other =>
      defaultValueBasedOnSchema(other)
  }

  private def defaultValueBasedOnSchema(schema: JsonSchema): Option[Json] = schema match {
    case _: EmptySchema => Some(Json.obj())
    case objSchema: ObjectSchema =>
      val props    = ListMap(objSchema.getPropertySchemas.asScala.toList.sortBy(_._1): _*)
      val required = objSchema.getRequiredProperties.asScala.toSet
      val fields = props.flatMap { case (key, subSchema) =>
        defaultJsonFor(subSchema) match {
          case Some(value)                    => Some(key -> value)
          case None if required.contains(key) => Some(key -> Json.Null)
          case _                              => None
        }
      }
      Some(Json.obj(fields.toSeq: _*))
    case arraySchema: ArraySchema =>
      Option(arraySchema.getAllItemSchema)
        .flatMap(defaultValueBasedOnSchema)
        .map(Json.arr(_))
        .orElse(Some(Json.arr()))
    case combinedSchema: CombinedSchema =>
      val criterion  = combinedSchema.getCriterion
      val subschemas = combinedSchema.getSubschemas.asScala.toSeq
      criterion match {
        case CombinedSchema.ALL_CRITERION =>
          subschemas.collectFirst { case schema: EnumSchema =>
            schema.getPossibleValuesAsList.asScala.headOption.map(jsonToCirce)
          } match {
            case Some(json) =>
              json
            case None =>
              // Merge all defaults
              val mergedFields = subschemas
                .flatMap(schema => defaultJsonFor(schema))
                .collect { case jsonObj if jsonObj.isObject => jsonObj.asObject.get.toMap }
                .foldLeft(Map.empty[String, Json])(_ ++ _)
              Some(Json.fromFields(mergedFields))
          }
        case CombinedSchema.ANY_CRITERION | CombinedSchema.ONE_CRITERION =>
          // Pick the first schema with extractable defaults
          subschemas
            .to(LazyList)
            .flatMap(schema => defaultJsonFor(schema))
            .headOption
        case _ => None
      }
    case refSchema: ReferenceSchema             => defaultJsonFor(refSchema.getReferredSchema)
    case _: StringSchema                        => Some(Json.fromString("#{ '' }"))
    case s: NumberSchema if s.requiresInteger() => Some(Json.fromInt(0))
    case _: NumberSchema                        => Some(Json.fromDoubleOrNull(0.0))
    case _: BooleanSchema                       => Some(Json.fromBoolean(true))
    case s: EnumSchema                          => s.getPossibleValuesAsList.asScala.headOption.map(jsonToCirce)
    case s: NullSchema                          => Some(Json.Null)
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

object NoSchemaJsonSupport extends ParsedSchemaSupport[OpenAPIJsonSchema] {

  private final val jsonSupport = JsonSchemaSupport

  override def payloadDeserializer: UniversalSchemaPayloadDeserializer = jsonSupport.payloadDeserializer

  override def serializer(schemaOpt: Option[ParsedSchema], c: SchemaRegistryClient, isKey: Boolean): Serializer[Any] =
    jsonSupport.serializer(schemaOpt, c, isKey)

  override def typeDefinition(schema: ParsedSchema): TypingResult = jsonSupport.typeDefinition(schema)

  override def formValueEncoder(schema: ParsedSchema, mode: ValidationMode): Any => AnyRef =
    jsonSupport.formValueEncoder(schema, mode)

  override def recordFormatterSupport(schemaRegistryClient: SchemaRegistryClient): RecordFormatterSupport =
    jsonSupport.recordFormatterSupport(schemaRegistryClient)

  override def extractSingleParameterForSink(
      schema: ParsedSchema,
      parameterExtractionMode: ParameterExtractionMode,
      validationMode: ValidationMode,
      rawParameter: Parameter
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SingleSchemaBasedParameter] = {
    jsonSupport.extractSingleParameterForSink(schema, parameterExtractionMode, validationMode, rawParameter)
  }

  override def extractParametersForSink(
      schema: ParsedSchema,
      restrictedParamNames: Set[ParameterName]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] =
    jsonSupport.extractParametersForSink(
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
