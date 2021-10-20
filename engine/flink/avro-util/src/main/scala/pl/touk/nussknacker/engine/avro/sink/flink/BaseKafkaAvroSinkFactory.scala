package pl.touk.nussknacker.engine.avro.sink.flink

import cats.data.Validated
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.process.SinkFactory
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.avro.encode.{OutputValidator, ValidationMode}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroSerializationSchemaFactory
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink
import pl.touk.nussknacker.engine.util.KeyedValue
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic}

abstract class BaseKafkaAvroSinkFactory extends SinkFactory {

  protected def createSink(preparedTopic: PreparedKafkaTopic,
                           version: SchemaVersionOption,
                           key: LazyParameter[AnyRef],
                           value: LazyParameter[AnyRef],
                           kafkaConfig: KafkaConfig,
                           serializationSchemaFactory: KafkaAvroSerializationSchemaFactory,
                           schemaDeterminer: AvroSchemaDeterminer, validationMode: ValidationMode)
                          (implicit processMetaData: MetaData,
                           nodeId: NodeId): FlinkSink = {
    //This is a bit redundant, since we already validate during creation
    val schemaData = schemaDeterminer.determineSchemaUsedInTyping.valueOr(SchemaDeterminerErrorHandler.handleSchemaRegistryErrorAndThrowException)
    val returnType = value.returnType
    validateValueType(returnType, schemaData.schema, validationMode).valueOr(err => throw new CustomNodeValidationException(err.message, err.paramName, null))
    val schemaUsedInRuntime = schemaDeterminer.toRuntimeSchema(schemaData)

    val clientId = s"${processMetaData.id}-${preparedTopic.prepared}"
    new KafkaAvroSink(preparedTopic, version, key, AvroSinkSingleValue(value, returnType), kafkaConfig, serializationSchemaFactory,
      schemaData.serializableSchema, schemaUsedInRuntime.map(_.serializableSchema), clientId, validationMode)
  }

  /**
    * Currently we check only required fields, because our typing mechanism doesn't support optionally fields
    */
  protected def validateValueType(valueType: TypingResult, schema: Schema, validationMode: ValidationMode)(implicit nodeId: NodeId): Validated[CustomNodeError, Unit] = {
    OutputValidator.validateOutput(valueType, schema, validationMode)
  }

}
