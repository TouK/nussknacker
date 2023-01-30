package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

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
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{AvroSchemaWithJsonPayload, OpenAPIJsonSchema}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.jsonpayload.ConfluentJsonPayloadKafkaSerializer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.formatter.AvroMessageReader
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
  override def payloadDeserializer(kafkaConfig: KafkaConfig): UniversalSchemaPayloadDeserializer = AvroPayloadDeserializer(kafkaConfig)

  override def serializer(schemaOpt: Option[ParsedSchema], client: SchemaRegistryClient, kafkaConfig: KafkaConfig, isKey: Boolean): Serializer[Any] = {
    ConfluentKafkaAvroSerializer(kafkaConfig, client, schemaOpt.map(_.cast()), isKey = isKey)
  }

  override def typeDefinition(schema: ParsedSchema): TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.cast().rawSchema())

  override def extractSinkValueParameter(schema: ParsedSchema)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] = AvroSinkValueParameter(schema.cast().rawSchema())

  override def sinkValueEncoder(schema: ParsedSchema, validationMode: ValidationMode): Any => AnyRef = {
    val encoder = BestEffortAvroEncoder(validationMode)
    (value: Any) => encoder.encodeOrError(value, schema.cast().rawSchema())
  }

  override def validateRawOutput(schema: ParsedSchema, t: TypingResult, mode: ValidationMode): ValidatedNel[OutputValidatorError, Unit] =
    new AvroSchemaOutputValidator(mode).validateTypingResultAgainstSchema(t, schema.cast().rawSchema())

  override def recordFormatterSupport(kafkaConfig: KafkaConfig, schemaRegistryClient: SchemaRegistryClient): RecordFormatterSupport = {
    // We pass None to schema, because message readers should not do schema evolution.
    // It is done this way because we want to keep messages in the original format as they were serialized on Kafka
    val createSerializer = serializer(None, schemaRegistryClient, kafkaConfig, _)
    val avroKeySerializer = createSerializer(true)
    val avroValueSerializer = createSerializer(false)
    new AvroPayloadRecordFormatterSupport(new AvroMessageReader(avroKeySerializer), new AvroMessageReader(avroValueSerializer))
  }
}


object JsonSchemaSupport extends ParsedSchemaSupport[OpenAPIJsonSchema] {
  override def payloadDeserializer(k: KafkaConfig): UniversalSchemaPayloadDeserializer = JsonSchemaPayloadDeserializer

  override def serializer(schemaOpt: Option[ParsedSchema], c: SchemaRegistryClient, k: KafkaConfig, isKey: Boolean): Serializer[Any] = (topic: String, data: Any) => data match {
    case j: Json => j.noSpaces.getBytes()
    case _ => throw new SerializationException(s"Expecting json but got: $data")
  }

  override def typeDefinition(schema: ParsedSchema): TypingResult = schema.cast().returnType

  override def extractSinkValueParameter(schema: ParsedSchema)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] =
    JsonSinkValueParameter(schema.cast().rawSchema(), defaultParamName = SinkValueParamName)

  override def sinkValueEncoder(schema: ParsedSchema, mode: ValidationMode): Any => AnyRef = {
    (value: Any) => BestEffortJsonSchemaEncoder.encodeOrError(value, schema.cast().rawSchema())
  }

  override def validateRawOutput(schema: ParsedSchema, t: TypingResult, mode: ValidationMode): ValidatedNel[OutputValidatorError, Unit] =
    new JsonSchemaOutputValidator(mode).validateTypingResultAgainstSchema(t, schema.cast().rawSchema())

  override def recordFormatterSupport(kafkaConfig: KafkaConfig, schemaRegistryClient: SchemaRegistryClient): RecordFormatterSupport =
    JsonPayloadRecordFormatterSupport
}


object AvroSchemaWithJsonPayloadSupport extends ParsedSchemaSupport[AvroSchemaWithJsonPayload] {

  override def payloadDeserializer(k: KafkaConfig): UniversalSchemaPayloadDeserializer = JsonPayloadDeserializer

  override def serializer(schemaOpt: Option[ParsedSchema], client: SchemaRegistryClient, kafkaConfig: KafkaConfig, isKey: Boolean): Serializer[Any] =
    new ConfluentJsonPayloadKafkaSerializer(kafkaConfig, client, new DefaultAvroSchemaEvolution, schemaOpt.map(_.cast().avroSchema), isKey = isKey)

  override def typeDefinition(schema: ParsedSchema): TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.cast().rawSchema())

  override def extractSinkValueParameter(schema: ParsedSchema)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] =  AvroSinkValueParameter(schema.cast().rawSchema())

  override def sinkValueEncoder(schema: ParsedSchema, mode: ValidationMode): Any => AnyRef = {
    val encoder = BestEffortAvroEncoder(mode)
    (value: Any) => encoder.encodeOrError(value, schema.cast().rawSchema())
  }

  override def validateRawOutput(schema: ParsedSchema, t: TypingResult, mode: ValidationMode): ValidatedNel[OutputValidatorError, Unit] =
    new AvroSchemaOutputValidator(mode).validateTypingResultAgainstSchema(t, schema.cast().rawSchema())

  override def recordFormatterSupport(kafkaConfig: KafkaConfig, schemaRegistryClient: SchemaRegistryClient): RecordFormatterSupport =
    JsonPayloadRecordFormatterSupport
}
