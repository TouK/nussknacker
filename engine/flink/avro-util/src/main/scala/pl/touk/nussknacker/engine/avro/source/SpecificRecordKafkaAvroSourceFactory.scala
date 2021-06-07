package pl.touk.nussknacker.engine.avro.source

import cats.data.Validated.Valid
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SchemaVersionParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryProvider, SpecificRecordEmbeddedSchemaDeterminer}
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory.KafkaAvroSourceFactoryState
import pl.touk.nussknacker.engine.flink.api.process.FlinkSource
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

import scala.reflect._

/**
 * Source factory for specific records - mainly generated from schema.
 */
class SpecificRecordKafkaAvroSourceFactory[V <: SpecificRecord: ClassTag](schemaRegistryProvider: SchemaRegistryProvider,
                                                                          processObjectDependencies: ProcessObjectDependencies,
                                                                          timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[Any, V]]])
  extends KafkaAvroSourceFactory[Any, V](schemaRegistryProvider, processObjectDependencies, timestampAssigner) {

  override def initialParameters: List[Parameter] = Nil

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition =
    topicParamStep orElse {
      case step@TransformationStep((TopicParamName, DefinedEagerParameter(topic:String, _)) :: Nil, _) =>
        val preparedTopic = prepareTopic(topic)

        // Here do not use prepareValueSchemaDeterminer because we don't want to provide schema version (we want to use the exact schema related to SpecificRecord)
        val valueSchemaDeterminer = new SpecificRecordEmbeddedSchemaDeterminer(classTag[V].runtimeClass.asInstanceOf[Class[_ <: SpecificRecord]])
        val valueValidationResult = determineSchemaAndType(valueSchemaDeterminer, Some(SchemaVersionParamName))

        prepareSourceFinalResults(preparedTopic, valueValidationResult, context, dependencies, step.parameters)

      case step@TransformationStep((TopicParamName, _) :: Nil, _) =>
        prepareSourceFinalErrors(context, dependencies, step.parameters, List(CustomNodeError("Topic/Version is not defined", Some(TopicParamName))))
    }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkSource[ConsumerRecord[Any, V]] = {
    val preparedTopic = extractPreparedTopic(params)
    val KafkaAvroSourceFactoryState(keySchemaDataUsedInRuntime, valueSchemaUsedInRuntime, kafkaContextInitializer) = finalState.get
    createSource(
      preparedTopic,
      kafkaConfig,
      schemaRegistryProvider.deserializationSchemaFactory,
      schemaRegistryProvider.recordFormatterFactory,
      keySchemaDataUsedInRuntime,
      valueSchemaUsedInRuntime,
      kafkaContextInitializer
    )(typedDependency[MetaData](dependencies), typedDependency[NodeId](dependencies))
  }

}