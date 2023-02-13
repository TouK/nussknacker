package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.json.JsonSinkValueParameter
import pl.touk.nussknacker.engine.json.encode.{BestEffortJsonSchemaEncoder, JsonSchemaOutputValidator}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.SinkValueParamName
import pl.touk.nussknacker.engine.schemedkafka.encode._
import pl.touk.nussknacker.engine.schemedkafka.schema.DefaultAvroSchemaEvolution
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.AzureSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.serialization.AzureAvroSerializerFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, OpenAPIJsonSchema}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.jsonpayload.ConfluentJsonPayloadKafkaSerializer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.formatter.AvroMessageReader
import pl.touk.nussknacker.engine.schemedkafka.sink.AvroSinkValueParameter
import pl.touk.nussknacker.engine.schemedkafka.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.util.output.{OutputValidatorError, OutputValidatorErrorsConverter}
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.{SinkSingleValueParameter, SinkValueParameter}

sealed trait ParsedSchemaSupport[+S <: ParsedSchema] extends UniversalSchemaSupport {
  protected implicit class RichParsedSchema(p: ParsedSchema){
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

  override def serializer(schemaOpt: Option[ParsedSchema], client: SchemaRegistryClient, isKey: Boolean): Serializer[Any] = {
    client match {
      case confluentClient: ConfluentSchemaRegistryClient if kafkaConfig.avroAsJsonSerialization.contains(true) =>
        new ConfluentJsonPayloadKafkaSerializer(kafkaConfig, confluentClient, new DefaultAvroSchemaEvolution, schemaOpt.map(_.cast()), isKey = isKey)
      case confluentClient: ConfluentSchemaRegistryClient =>
        ConfluentKafkaAvroSerializer(kafkaConfig, confluentClient, schemaOpt.map(_.cast()), isKey = isKey)
      case azureClient: AzureSchemaRegistryClient =>
        AzureAvroSerializerFactory.createSerializer(azureClient, kafkaConfig, schemaOpt.map(_.cast()), isKey)
      case _ =>
        throw new IllegalArgumentException(s"Not supported schema registry client: ${client.getClass}. " +
          s"Avro serialization is currently supported only for Confluent schema registry implementation")
    }

  }

  override def typeDefinition(schema: ParsedSchema): TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema.cast().rawSchema())

  override def extractSinkValueParameter(schema: ParsedSchema, rawMode: Boolean, validationMode: ValidationMode, rawParameter: Parameter)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] = AvroSinkValueParameter(schema.cast().rawSchema())

  override def sinkValueEncoder(schema: ParsedSchema, validationMode: ValidationMode): Any => AnyRef = {
    val encoder = BestEffortAvroEncoder(validationMode)
    (value: Any) => encoder.encodeOrError(value, schema.cast().rawSchema())
  }

  override def validateRawOutput(schema: ParsedSchema, t: TypingResult, mode: ValidationMode): ValidatedNel[OutputValidatorError, Unit] =
    new AvroSchemaOutputValidator(mode).validateTypingResultAgainstSchema(t, schema.cast().rawSchema())

  override def recordFormatterSupport(schemaRegistryClient: SchemaRegistryClient): RecordFormatterSupport = {
    if (kafkaConfig.avroAsJsonSerialization.contains(true)) {
      JsonPayloadRecordFormatterSupport
    } else {
      // We pass None to schema, because message readers should not do schema evolution.
      // It is done this way because we want to keep messages in the original format as they were serialized on Kafka
      val createSerializer = serializer(None, schemaRegistryClient, _)
      val avroKeySerializer = createSerializer(true)
      val avroValueSerializer = createSerializer(false)
      new AvroPayloadRecordFormatterSupport(new AvroMessageReader(avroKeySerializer), new AvroMessageReader(avroValueSerializer))
    }
  }
}


object JsonSchemaSupport extends ParsedSchemaSupport[OpenAPIJsonSchema] {
  override val payloadDeserializer: UniversalSchemaPayloadDeserializer = JsonSchemaPayloadDeserializer
  private val outputValidatorErrorsConverter = new OutputValidatorErrorsConverter(SinkValueParamName)

  override def serializer(schemaOpt: Option[ParsedSchema], c: SchemaRegistryClient, isKey: Boolean): Serializer[Any] =
    (topic: String, data: Any) => data match {
      case j: Json => j.noSpaces.getBytes()
      case _ => throw new SerializationException(s"Expecting json but got: $data")
    }

  override def typeDefinition(schema: ParsedSchema): TypingResult = schema.cast().returnType

  override def extractSinkValueParameter(schema: ParsedSchema, rawMode: Boolean, validationMode: ValidationMode, rawParameter: Parameter)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] = {
    if (rawMode) {
      Validated.Valid(
        SinkSingleValueParameter(rawParameter, new JsonSchemaOutputValidator(validationMode, schema.cast().rawSchema(), schema.cast().rawSchema()))
      )
    } else {
      JsonSinkValueParameter(schema.cast().rawSchema(), defaultParamName = SinkValueParamName, ValidationMode.lax)
    }
  }

  override def sinkValueEncoder(schema: ParsedSchema, mode: ValidationMode): Any => AnyRef = {
    (value: Any) => BestEffortJsonSchemaEncoder.encodeOrError(value, schema.cast().rawSchema())
  }

  override def validateRawOutput(schema: ParsedSchema, t: TypingResult, mode: ValidationMode): ValidatedNel[OutputValidatorError, Unit] = ???

  override def recordFormatterSupport(schemaRegistryClient: SchemaRegistryClient): RecordFormatterSupport =
    JsonPayloadRecordFormatterSupport
}
