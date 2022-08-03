package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.ValidatedNel
import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{AvroSchemaWithJsonPayload, ConfluentSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization._
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.output.OutputValidatorError
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.SinkValueParameter

object UniversalSchemaSupport {
  def forSchemaType(schemaType: String): UniversalSchemaSupport = schemaType match {
    case AvroSchema.TYPE => AvroSchemaSupport
    case AvroSchemaWithJsonPayload.TYPE => AvroSchemaWithJsonPayloadSupport
    case JsonSchema.TYPE => JsonSchemaSupport
    case _ => throw new UnsupportedSchemaType(schemaType)
  }
}

trait UniversalSchemaSupport {
  val payloadDeserializer: UniversalSchemaPayloadDeserializer
  def serializer[T](schema: ParsedSchema, c: ConfluentSchemaRegistryClient, k: KafkaConfig, isKey: Boolean): Serializer[T]
  def messageFormatter(c: SchemaRegistryClient): Any => Json
  def messageReader(schema: ParsedSchema, c: SchemaRegistryClient): (Json, String) => Array[Byte]
  def typeDefinition(schema: ParsedSchema): TypingResult
  def extractSinkValueParameter(schema: ParsedSchema)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter]
  def sinkValueEncoder(schema: ParsedSchema, mode: ValidationMode): Any => AnyRef
  def validateRawOutput(schema: ParsedSchema, t: TypingResult, mode: ValidationMode)(implicit nodeId: NodeId): ValidatedNel[OutputValidatorError, Unit]
}

class UnsupportedSchemaType(schemaType: String) extends IllegalArgumentException(s"Unsupported schema type: $schemaType")