package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent

import cats.data.ValidatedNel
import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.json.JsonSinkValueParameter
import pl.touk.nussknacker.engine.json.encode.{BestEffortJsonSchemaEncoder, JsonSchemaOutputValidator}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.SinkValueParamName
import pl.touk.nussknacker.engine.schemedkafka.encode._
import pl.touk.nussknacker.engine.schemedkafka.schema.DefaultAvroSchemaEvolution
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{AvroSchemaWithJsonPayload, ConfluentSchemaRegistryClient, OpenAPIJsonSchema}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.jsonpayload.JsonPayloadKafkaSerializer
import pl.touk.nussknacker.engine.schemedkafka.sink.AvroSinkValueParameter
import pl.touk.nussknacker.engine.schemedkafka.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.util.output.OutputValidatorError
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.SinkValueParameter

sealed trait ParsedSchemaSupport[+S <: ParsedSchema] extends UniversalSchemaSupport {
  protected implicit class RichParsedSchema(p: ParsedSchema){
    def cast(): S = p.asInstanceOf[S]
  }
}

object AvroSchemaSupport extends ParsedSchemaSupport[AvroSchema] {
  override val payloadDeserializer: UniversalSchemaPayloadDeserializer = ConfluentAvroPayloadDeserializer.default

  override def serializer[T](schema: ParsedSchema, client: ConfluentSchemaRegistryClient, kafkaConfig: KafkaConfig, isKey: Boolean): Serializer[T] =
    ConfluentKafkaAvroSerializer(kafkaConfig, client, Some(schema.cast()), isKey = isKey).asInstanceOf[Serializer[T]]

  override def typeDefinition(schema: ParsedSchema): TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.cast().rawSchema())

  override def extractSinkValueParameter(schema: ParsedSchema)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] = AvroSinkValueParameter(schema.cast().rawSchema())

  override def sinkValueEncoder(schema: ParsedSchema, validationMode: ValidationMode): Any => AnyRef = {
    val encoder = BestEffortAvroEncoder(validationMode)
    (value: Any) => encoder.encodeOrError(value, schema.cast().rawSchema())
  }

  override def validateRawOutput(schema: ParsedSchema, t: TypingResult, mode: ValidationMode)(implicit nodeId: NodeId): ValidatedNel[OutputValidatorError, Unit] =
    new AvroSchemaOutputValidator(mode).validateTypingResultToSchema(t, schema.cast().rawSchema())

  override val recordFormatterSupport: RecordFormatterSupport = AvroPayloadRecordFromatterSupport
}


object JsonSchemaSupport extends ParsedSchemaSupport[OpenAPIJsonSchema] {
  override val payloadDeserializer: UniversalSchemaPayloadDeserializer = ConfluentJsonSchemaPayloadDeserializer

  override def serializer[T](schema: ParsedSchema, c: ConfluentSchemaRegistryClient, k: KafkaConfig, isKey: Boolean): Serializer[T] = (topic: String, data: T) => data match {
    case j: Json => j.noSpaces.getBytes()
    case _ => throw new SerializationException(s"Expecting json but got: $data")
  }

  override def typeDefinition(schema: ParsedSchema): TypingResult = schema.cast().returnType

  override def extractSinkValueParameter(schema: ParsedSchema)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] =
    JsonSinkValueParameter(schema.cast().rawSchema(), defaultParamName = SinkValueParamName)

  override def sinkValueEncoder(schema: ParsedSchema, mode: ValidationMode): Any => AnyRef = {
    val encoder = new BestEffortJsonSchemaEncoder(mode)
    (value: Any) => encoder.encodeOrError(value, schema.cast().rawSchema())
  }

  override def validateRawOutput(schema: ParsedSchema, t: TypingResult, mode: ValidationMode)(implicit nodeId: NodeId): ValidatedNel[OutputValidatorError, Unit] =
    new JsonSchemaOutputValidator(mode).validateTypingResultAgainstSchema(t, schema.cast().rawSchema())

  override val recordFormatterSupport: RecordFormatterSupport = JsonPayloadRecordFormatterSupport
}


object AvroSchemaWithJsonPayloadSupport extends ParsedSchemaSupport[AvroSchemaWithJsonPayload] {

  override val payloadDeserializer: UniversalSchemaPayloadDeserializer = ConfluentJsonPayloadDeserializer

  override def serializer[T](schema: ParsedSchema, client: ConfluentSchemaRegistryClient, kafkaConfig: KafkaConfig, isKey: Boolean): Serializer[T] =
    new JsonPayloadKafkaSerializer(kafkaConfig, client, new DefaultAvroSchemaEvolution, Some(schema.cast().avroSchema), isKey = isKey).asInstanceOf[Serializer[T]]

  override def typeDefinition(schema: ParsedSchema): TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.cast().rawSchema())

  override def extractSinkValueParameter(schema: ParsedSchema)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] =  AvroSinkValueParameter(schema.cast().rawSchema())

  override def sinkValueEncoder(schema: ParsedSchema, mode: ValidationMode): Any => AnyRef = {
    val encoder = BestEffortAvroEncoder(mode)
    (value: Any) => encoder.encodeOrError(value, schema.cast().rawSchema())
  }

  override def validateRawOutput(schema: ParsedSchema, t: TypingResult, mode: ValidationMode)(implicit nodeId: NodeId): ValidatedNel[OutputValidatorError, Unit] =
    new AvroSchemaOutputValidator(mode).validateTypingResultToSchema(t, schema.cast().rawSchema())

  override val recordFormatterSupport: RecordFormatterSupport = JsonPayloadRecordFormatterSupport
}
