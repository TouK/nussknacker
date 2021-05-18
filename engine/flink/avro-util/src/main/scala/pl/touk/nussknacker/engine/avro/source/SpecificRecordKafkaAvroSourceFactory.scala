package pl.touk.nussknacker.engine.avro.source

import cats.data.Validated.Valid
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.avro.AvroSchemaDeterminer
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SchemaVersionParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.schemaregistry.{LatestSchemaVersion, SchemaRegistryProvider, SchemaVersionOption, SpecificRecordEmbeddedSchemaDeterminer}
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory.KafkaAvroSourceFactoryState
import pl.touk.nussknacker.engine.flink.api.process.FlinkSource
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.kafka.PreparedKafkaTopic

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
        val versionOption = LatestSchemaVersion

        val (keyValidationResult, keyErrors) = if (kafkaConfig.useStringForKey) {
          (Valid((None, Typed[String])), Nil)
        } else {
          determineSchemaAndType(prepareKeySchemaDeterminer(preparedTopic), Some(TopicParamName))
        }
        val (valueValidationResult, valueErrors) = determineSchemaAndType(prepareValueSchemaDeterminer(preparedTopic, versionOption), Some(SchemaVersionParamName))

        (keyValidationResult, valueValidationResult) match {
          case (Valid((keyRuntimeSchema, keyType)), Valid((valueRuntimeSchema, valueType))) =>
            val finalState = KafkaAvroSourceFactoryState(keyRuntimeSchema, valueRuntimeSchema)
            FinalResults(finalCtx(context, dependencies, step.parameters, step.state, keyType, valueType), state = Some(finalState))
          case _ =>
            FinalResults(finalCtx(context, dependencies, step.parameters, step.state, Unknown, Unknown), keyErrors ++ valueErrors, None)
        }
      case step@TransformationStep((TopicParamName, _) :: Nil, _) =>
        FinalResults(finalCtx(context, dependencies, step.parameters, step.state, Unknown, Unknown), Nil)
    }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkSource[ConsumerRecord[Any, V]] = {
    val preparedTopic = extractPreparedTopic(params)
    val KafkaAvroSourceFactoryState(keySchemaDataUsedInRuntime, valueSchemaUsedInRuntime) = finalState.get
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

  override protected def prepareValueSchemaDeterminer(preparedTopic: PreparedKafkaTopic, version: SchemaVersionOption): AvroSchemaDeterminer = {
    new SpecificRecordEmbeddedSchemaDeterminer(classTag[V].runtimeClass.asInstanceOf[Class[_ <: SpecificRecord]])
  }
}