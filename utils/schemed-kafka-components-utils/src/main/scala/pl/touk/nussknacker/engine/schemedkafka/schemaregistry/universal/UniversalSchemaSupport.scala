package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import cats.data.{Validated, ValidatedNel}
import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.FatalUnknownError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.json.JsonSinkValueParameter
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.KafkaFactory.SinkValueParamName
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.encode.BestEffortAvroEncoder
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.sink.AvroSinkValueParameter
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.SinkValueParameter

class UniversalSchemaSupportDispatcher private(kafkaConfig: KafkaConfig) {

  val supportBySchemaType: Map[String, UniversalSchemaSupport] =
    Map(
      AvroSchema.TYPE -> new AvroSchemaSupport(kafkaConfig),
      JsonSchema.TYPE -> JsonSchemaSupport)

  def forSchemaType(schemaType: String): UniversalSchemaSupport =
    supportBySchemaType.getOrElse(schemaType, throw new UnsupportedSchemaType(schemaType))

  def parametersFromRuntimeParsedSchema(determinedSchema: RuntimeSchemaData[ParsedSchema])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, List[Parameter]] = {
    determinedSchema.schema match {
      case s: AvroSchema => AvroSinkValueParameter(s.rawSchema()).map(_.toParameters)
      case s: JsonSchema => JsonSinkValueParameter(s.rawSchema(), SinkValueParamName, ValidationMode.lax)(nodeId).map(_.toParameters)
      case s => Validated.invalidNel(FatalUnknownError(s"Avro or Json schema is required, but got ${s.schemaType()}"))
    }
  }

  def messageFormatterFromParsedSchema(schema: ParsedSchema, schemaRegistryClient: SchemaRegistryClient): Any => Json = {
    schema match {
      case s: AvroSchema =>
        val encoder = BestEffortAvroEncoder(ValidationMode.lax)
        val recordFormatterSupport = new AvroSchemaSupport(kafkaConfig).recordFormatterSupport(schemaRegistryClient)
        (data: Any) => recordFormatterSupport.formatMessage(encoder.encodeOrError(data, s.rawSchema()))
      case _: JsonSchema => JsonPayloadRecordFormatterSupport.formatMessage
      case _ => throw new UnsupportedSchemaType(schema.schemaType())
    }
  }

}

object UniversalSchemaSupportDispatcher {
  def apply(kafkaConfig: KafkaConfig): UniversalSchemaSupportDispatcher = new UniversalSchemaSupportDispatcher(kafkaConfig)
}

trait UniversalSchemaSupport {
  def payloadDeserializer: UniversalSchemaPayloadDeserializer
  def serializer(schemaOpt: Option[ParsedSchema], c: SchemaRegistryClient, isKey: Boolean): Serializer[Any]
  def typeDefinition(schema: ParsedSchema): TypingResult
  def extractSinkValueParameter(schema: ParsedSchema, rawMode: Boolean, validationMode: ValidationMode, rawParameter: Parameter)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter]
  def sinkValueEncoder(schema: ParsedSchema, mode: ValidationMode): Any => AnyRef
  def recordFormatterSupport(schemaRegistryClient: SchemaRegistryClient): RecordFormatterSupport
}

class UnsupportedSchemaType(schemaType: String) extends IllegalArgumentException(s"Unsupported schema type: $schemaType")
