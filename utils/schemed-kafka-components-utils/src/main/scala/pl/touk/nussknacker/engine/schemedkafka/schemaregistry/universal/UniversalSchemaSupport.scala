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
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.sinkValueParamName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClient
import pl.touk.nussknacker.engine.util.parameters.SchemaBasedParameter

class UniversalSchemaSupportDispatcher private (kafkaConfig: KafkaConfig) {

  val supportBySchemaType: Map[String, UniversalSchemaSupport] =
    Map(AvroSchema.TYPE -> new AvroSchemaSupport(kafkaConfig), JsonSchema.TYPE -> JsonSchemaSupport)

  def forSchemaType(schemaType: String): UniversalSchemaSupport =
    supportBySchemaType.getOrElse(schemaType, throw new UnsupportedSchemaType(schemaType))
}

object UniversalSchemaSupportDispatcher {

  def apply(kafkaConfig: KafkaConfig): UniversalSchemaSupportDispatcher = new UniversalSchemaSupportDispatcher(
    kafkaConfig
  )

}

trait UniversalSchemaSupport {
  def payloadDeserializer: UniversalSchemaPayloadDeserializer
  def serializer(schemaOpt: Option[ParsedSchema], c: Option[SchemaRegistryClient], isKey: Boolean): Serializer[Any]
  def typeDefinition(schema: ParsedSchema): TypingResult
  def formValueEncoder(schema: ParsedSchema, mode: ValidationMode): Any => AnyRef
  def recordFormatterSupport(schemaRegistryClient: SchemaRegistryClient): RecordFormatterSupport

  def extractParameter(
      schema: ParsedSchema,
      rawMode: Boolean,
      validationMode: ValidationMode,
      rawParameter: Parameter,
      restrictedParamNames: Set[ParameterName]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SchemaBasedParameter]

  final def extractParameters(
      schema: ParsedSchema
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, List[Parameter]] = {
    extractParameter(
      schema,
      rawMode = false,
      validationMode = ValidationMode.lax,
      rawParameter = Parameter[AnyRef](sinkValueParamName),
      restrictedParamNames = Set.empty
    ).map(_.toParameters)
  }

  final def prepareMessageFormatter(schema: ParsedSchema, schemaRegistryClient: SchemaRegistryClient): Any => Json = {
    val recordFormatter = recordFormatterSupport(schemaRegistryClient)
    val encodeRecord    = formValueEncoder(schema, ValidationMode.lax)
    (data: Any) => recordFormatter.formatMessage(encodeRecord(data))
  }

}

class UnsupportedSchemaType(schemaType: String)
    extends IllegalArgumentException(s"Unsupported schema type: $schemaType")
