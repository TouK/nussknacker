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
import pl.touk.nussknacker.engine.avro.encode._
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{AvroSchemaWithJsonPayload, ConfluentSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization._
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.output.OutputValidatorError
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.SinkValueParameter

object UniversalSchemaSupport {

  implicit class RichParsedSchema(parsedSchema: ParsedSchema) extends UniversalSchemaSupport {
    private lazy val support: ParsedSchemaSupport[ParsedSchema] = parsedSchema match {
      case schema: AvroSchema => AvroSchemaSupport(schema)
      case schema: AvroSchemaWithJsonPayload => AvroSchemaWithJsonPayloadSupport(schema)
      case schema: JsonSchema => JsonSchemaSupport(schema)
      case _ => throw new UnsupportedSchemaType(parsedSchema)
    }

    override val payloadDeserializer: UniversalSchemaPayloadDeserializer = support.payloadDeserializer
    override def serializerFactory[T]: (ConfluentSchemaRegistryClient, KafkaConfig, Boolean) => Serializer[T] = support.serializerFactory
    override def messageFormatterFactory: SchemaRegistryClient => Any => Json = support.messageFormatterFactory
    override def messageReaderFactory: SchemaRegistryClient => (Json, String) => Array[Byte] = support.messageReaderFactory
    override def typeDefinition: TypingResult = support.typeDefinition
    override def extractSinkValueParameter(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] = support.extractSinkValueParameter
    override def sinkValueEncoderFactory: ValidationMode => Any => AnyRef = support.sinkValueEncoderFactory
    override def rawOutputValidatorFactory(implicit nodeId: NodeId): (TypingResult, ValidationMode) => ValidatedNel[OutputValidatorError, Unit] = support.rawOutputValidatorFactory
  }
}

trait UniversalSchemaSupport {
  val payloadDeserializer: UniversalSchemaPayloadDeserializer
  def serializerFactory[T]: (ConfluentSchemaRegistryClient, KafkaConfig, Boolean) => Serializer[T]
  def messageFormatterFactory: SchemaRegistryClient => Any => Json
  def messageReaderFactory: SchemaRegistryClient => (Json, String) => Array[Byte]
  def typeDefinition: TypingResult
  def extractSinkValueParameter(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter]
  def sinkValueEncoderFactory: ValidationMode => Any => AnyRef
  def rawOutputValidatorFactory(implicit nodeId: NodeId): (TypingResult, ValidationMode) => ValidatedNel[OutputValidatorError, Unit]
}

class UnsupportedSchemaType(parsedSchema: ParsedSchema) extends IllegalArgumentException(s"Unsupported schema type: ${parsedSchema.schemaType()}")