package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import cats.data.ValidatedNel
import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ContentTypesSchemas, SchemaRegistryClient}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaSupport.ParameterExtractionMode
import pl.touk.nussknacker.engine.util.parameters.{SchemaBasedParameter, SingleSchemaBasedParameter}

class UniversalSchemaSupportDispatcher private (kafkaConfig: KafkaConfig) {

  val supportBySchemaType: Map[String, UniversalSchemaSupport] =
    Map(
      AvroSchema.TYPE -> new AvroSchemaSupport(kafkaConfig),
      JsonSchema.TYPE -> JsonSchemaSupport
    )

  def forSchemaType(schemaType: String): UniversalSchemaSupport =
    supportBySchemaType.getOrElse(schemaType, throw new UnsupportedSchemaType(schemaType))

  def forParsedSchema(parsedSchema: ParsedSchema): UniversalSchemaSupport = parsedSchema match {
    // For ad hoc tests we want to present the user with json editor when topic has no schema and content type Json was selected
    case ContentTypesSchemas.schemaForJson => NoSchemaJsonSupport
    case _                                 => forSchemaType(parsedSchema.schemaType())
  }

}

object UniversalSchemaSupportDispatcher {

  def apply(kafkaConfig: KafkaConfig): UniversalSchemaSupportDispatcher = new UniversalSchemaSupportDispatcher(
    kafkaConfig
  )

}

trait UniversalSchemaSupport {
  def payloadDeserializer: UniversalSchemaPayloadDeserializer
  def serializer(schemaOpt: Option[ParsedSchema], c: SchemaRegistryClient, isKey: Boolean): Serializer[Any]
  def typeDefinition(schema: ParsedSchema): TypingResult
  def formValueEncoder(schema: ParsedSchema, mode: ValidationMode): Any => AnyRef
  def recordFormatterSupport(schemaRegistryClient: SchemaRegistryClient): RecordFormatterSupport

  def extractSingleParameterForSink(
      schema: ParsedSchema,
      parameterExtractionMode: ParameterExtractionMode,
      validationMode: ValidationMode,
      rawParameter: Parameter
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SingleSchemaBasedParameter]

  def extractParametersForSink(
      schema: ParsedSchema,
      restrictedParamNames: Set[ParameterName]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SchemaBasedParameter]

  def extractParameterForTests(
      schema: ParsedSchema
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SchemaBasedParameter]

  final def prepareMessageFormatter(schema: ParsedSchema, schemaRegistryClient: SchemaRegistryClient): Any => Json = {
    val recordFormatter = recordFormatterSupport(schemaRegistryClient)
    val encodeRecord    = formValueEncoder(schema, ValidationMode.lax)
    (data: Any) => recordFormatter.formatMessage(encodeRecord(data))
  }

}

object UniversalSchemaSupport {
  sealed trait ParameterExtractionMode

  object ParameterExtractionMode {
    case object RawParameter         extends ParameterExtractionMode
    case object RawParameterTemplate extends ParameterExtractionMode
  }

}

class UnsupportedSchemaType(schemaType: String)
    extends IllegalArgumentException(s"Unsupported schema type: $schemaType")
