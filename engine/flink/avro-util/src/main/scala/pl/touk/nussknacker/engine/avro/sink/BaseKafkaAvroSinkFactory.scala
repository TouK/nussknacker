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
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.SinkOutputParamName
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroSerializationSchemaFactory
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic}

abstract class BaseKafkaAvroSinkFactory extends SinkFactory {

  override def requiresOutput: Boolean = false

  protected def createSink(preparedTopic: PreparedKafkaTopic,
                           version: Option[Int],
                           output: LazyParameter[AnyRef],
                           kafkaConfig: KafkaConfig,
                           serializationSchemaFactory: KafkaAvroSerializationSchemaFactory,
                           schemaDeterminer: AvroSchemaDeterminer)
                          (implicit processMetaData: MetaData,
                           nodeId: NodeId): FlinkSink = {
    //This is a bit redundant, since we already validate during creation
    val schema = schemaDeterminer.determineSchemaUsedInTyping.valueOr(SchemaDeterminerErrorHandler.handleSchemaRegistryErrorAndThrowException)
    validateOutput(output.returnType, schema).valueOr(err => throw new CustomNodeValidationException(err.message, err.paramName, null))
    val schemaUsedInRuntime = schemaDeterminer.toRuntimeSchema(schema)

    val clientId = s"${processMetaData.id}-${preparedTopic.prepared}"
    new KafkaAvroSink(preparedTopic, version, output, kafkaConfig, serializationSchemaFactory,
      new NkSerializableAvroSchema(schema), schemaUsedInRuntime.map(new NkSerializableAvroSchema(_)), clientId)
  }

  /**
    * Currently we check only required fields, because our typing mechanism doesn't support optionally fields
    */
  protected def validateOutput(output: TypingResult, schema: Schema)(implicit nodeId: NodeId): Validated[CustomNodeError, Unit] = {
    val possibleTypes = AvroSchemaTypeDefinitionExtractor.ExtendedPossibleTypes
    val returnType = AvroSchemaTypeDefinitionExtractor.typeDefinitionWithoutNullableFields(schema, possibleTypes)
    if (!output.canBeSubclassOf(returnType)) {
      Invalid(CustomNodeError("Provided output doesn't match to selected avro schema.", Some(SinkOutputParamName)))
    } else {
      Valid(())
    }
  }

}
