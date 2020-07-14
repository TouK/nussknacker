package pl.touk.nussknacker.engine.avro.sink

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.process.SinkFactory
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.SinkOutputParamName
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink
import pl.touk.nussknacker.engine.kafka.serialization.KafkaVersionAwareSerializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic}

abstract class BaseKafkaAvroSinkFactory extends SinkFactory {

  override def requiresOutput: Boolean = false

  // We currently not using nodeId but it is here in case if someone want to use in their own concrete implementation
  protected def createSink(preparedTopic: PreparedKafkaTopic,
                           version: Option[Int],
                           output: LazyParameter[AnyRef],
                           kafkaConfig: KafkaConfig,
                           serializationSchemaFactory: KafkaVersionAwareSerializationSchemaFactory[AnyRef],
                           schemaDeterminer: AvroSchemaDeterminer,
                           processMetaData: MetaData,
                           nodeId: NodeId): FlinkSink = {

    //This is a bit redundant, since we already validate during creation
    validateOutput(output.returnType, schemaDeterminer)(nodeId).swap.foreach { error =>
      throw new CustomNodeValidationException(error.message, error.paramName, null)
    }

    val clientId = s"${processMetaData.id}-${preparedTopic.prepared}"
    new KafkaAvroSink(preparedTopic, version, output, kafkaConfig, serializationSchemaFactory, schemaDeterminer, clientId)
  }

  /**
    * Currently we check only required fields, because our typing mechanism doesn't support optionally fields
    */
  protected def validateOutput(output: TypingResult, schemaDeterminer: AvroSchemaDeterminer)(implicit nodeId: NodeId): Validated[CustomNodeError, Unit] = {
    schemaDeterminer.determineSchemaUsedInTyping
      .leftMap(SchemaDeterminerErrorHandler.handleSchemaRegistryError)
      .andThen { schema =>
        val possibleTypes = AvroSchemaTypeDefinitionExtractor.ExtendedPossibleTypes
        val returnType = AvroSchemaTypeDefinitionExtractor.typeDefinitionWithoutNullableFields(schema, possibleTypes)
        if (!output.canBeSubclassOf(returnType)) {
          Invalid(CustomNodeError("Provided output doesn't match to selected avro schema.", Some(SinkOutputParamName)))
        } else {
          Valid(())
        }
      }

  }
}
