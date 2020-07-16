package pl.touk.nussknacker.engine.avro.sink

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import org.apache.avro.Schema
import org.apache.flink.formats.avro.typeutils.NkSerializableAvroSchema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.process.SinkFactory
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.SinkValueParamName
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.SinkOutputParamName
import pl.touk.nussknacker.engine.avro.encode.{EncoderPolicy, OutputValidator}
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroSerializationSchemaFactory
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic}

abstract class BaseKafkaAvroSinkFactory extends SinkFactory {

  override def requiresOutput: Boolean = false

  protected def createSink(preparedTopic: PreparedKafkaTopic,
                           version: Option[Int],
                           key: LazyParameter[AnyRef],
                           value: LazyParameter[AnyRef],
                           kafkaConfig: KafkaConfig,
                           serializationSchemaFactory: KafkaAvroSerializationSchemaFactory,
                           schemaDeterminer: AvroSchemaDeterminer, encoderPolicy: EncoderPolicy)
                          (implicit processMetaData: MetaData,
                           nodeId: NodeId): FlinkSink = {
    //This is a bit redundant, since we already validate during creation
    val schema = schemaDeterminer.determineSchemaUsedInTyping.valueOr(SchemaDeterminerErrorHandler.handleSchemaRegistryErrorAndThrowException)
    validateValueType(value.returnType, schema, encoderPolicy).valueOr(err => throw new CustomNodeValidationException(err.message, err.paramName, null))
    val schemaUsedInRuntime = schemaDeterminer.toRuntimeSchema(schema)

    val clientId = s"${processMetaData.id}-${preparedTopic.prepared}"
    new KafkaAvroSink(preparedTopic, version, key, value, kafkaConfig, serializationSchemaFactory,
      new NkSerializableAvroSchema(schema), schemaUsedInRuntime.map(new NkSerializableAvroSchema(_)), clientId, encoderPolicy)
  }

  /**
    * Currently we check only required fields, because our typing mechanism doesn't support optionally fields
    */
  protected def validateValueType(valueType: TypingResult, schema: Schema, encoderPolicy: EncoderPolicy)(implicit nodeId: NodeId): Validated[CustomNodeError, Unit] = {
    OutputValidator.validateOutput(valueType, schema, encoderPolicy)
  }

}
