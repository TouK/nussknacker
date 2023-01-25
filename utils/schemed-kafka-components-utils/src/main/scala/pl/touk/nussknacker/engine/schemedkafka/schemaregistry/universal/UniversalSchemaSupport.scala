package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import cats.data.ValidatedNel
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.AvroSchemaWithJsonPayload
import pl.touk.nussknacker.engine.util.output.OutputValidatorError
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.SinkValueParameter

object UniversalSchemaSupport {
  val supportedSchemaTypes: Set[String] = Set(AvroSchema.TYPE, AvroSchemaWithJsonPayload.TYPE, JsonSchema.TYPE)

  def forSchemaType(schemaType: String): UniversalSchemaSupport = schemaType match {
    case AvroSchema.TYPE => AvroSchemaSupport
    case AvroSchemaWithJsonPayload.TYPE => AvroSchemaWithJsonPayloadSupport
    case JsonSchema.TYPE => JsonSchemaSupport
    case _ => throw new UnsupportedSchemaType(schemaType)
  }

}

trait UniversalSchemaSupport {
  def payloadDeserializer(k: KafkaConfig): UniversalSchemaPayloadDeserializer
  def serializer(schemaOpt: Option[ParsedSchema], c: SchemaRegistryClient, k: KafkaConfig, isKey: Boolean): Serializer[Any]
  def typeDefinition(schema: ParsedSchema): TypingResult
  def extractSinkValueParameter(schema: ParsedSchema)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter]
  def sinkValueEncoder(schema: ParsedSchema, mode: ValidationMode): Any => AnyRef
  def validateRawOutput(schema: ParsedSchema, t: TypingResult, mode: ValidationMode): ValidatedNel[OutputValidatorError, Unit]
  def recordFormatterSupport(kafkaConfig: KafkaConfig, schemaRegistryClient: SchemaRegistryClient): RecordFormatterSupport
}

class UnsupportedSchemaType(schemaType: String) extends IllegalArgumentException(s"Unsupported schema type: $schemaType")